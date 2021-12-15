#ifdef __linux__

#include <codecvt>
#include <dlfcn.h>
#include <locale>
#include <string>
#include <sys/ioctl.h>
#include <unistd.h>

#include "linux_fsnotifier.h"

#define EVENT_BUFFER_SIZE (16 * 1024)

#define EVENT_MASK (IN_CREATE | IN_DELETE | IN_DELETE_SELF | IN_EXCL_UNLINK | IN_MODIFY | IN_MOVE_SELF | IN_MOVED_FROM | IN_MOVED_TO | IN_ONLYDIR)

InotifyInstanceLimitTooLowException::InotifyInstanceLimitTooLowException()
    : InsufficientResourcesFileWatcherException("Inotify instance limit too low") {
}

InotifyWatchesLimitTooLowException::InotifyWatchesLimitTooLowException()
    : InsufficientResourcesFileWatcherException("Inotify watches limit too low") {
}

WatchPoint::WatchPoint(const u16string& path, shared_ptr<Inotify> inotify, int watchDescriptor, ino_t inode)
    : status(WatchPointStatus::LISTENING)
    , watchDescriptor(watchDescriptor)
    , inotify(inotify)
    , path(path)
    , inode(inode) {
}

CancelResult WatchPoint::cancel() {
    if (status == WatchPointStatus::CANCELLED) {
        return CancelResult::ALREADY_CANCELLED;
    }
    status = WatchPointStatus::CANCELLED;
    if (inotify_rm_watch(inotify->fd, watchDescriptor) != 0) {
        switch (errno) {
            case EINVAL:
                logToJava(LogLevel::INFO, "Couldn't stop watching %s (probably because the directory was removed)", utf16ToUtf8String(path).c_str());
                return CancelResult::NOT_CANCELLED;
                break;
            default:
                throw FileWatcherException("Couldn't stop watching", path, errno);
        }
    }
    return CancelResult::CANCELLED;
}

Inotify::Inotify()
    : fd(inotify_init1(IN_CLOEXEC | IN_NONBLOCK)) {
    if (fd == -1) {
        if (errno == EMFILE) {
            throw InotifyInstanceLimitTooLowException();
        }
        throw FileWatcherException("Couldn't register inotify handle", errno);
    }
}

Inotify::~Inotify() {
    close(fd);
}

ShutdownEvent::ShutdownEvent()
    : fd(eventfd(0, 0)) {
    if (fd == -1) {
        throw FileWatcherException("Couldn't register event source", errno);
    }
}
ShutdownEvent::~ShutdownEvent() {
    close(fd);
}

void ShutdownEvent::trigger() const {
    const uint64_t increment = 1;
    write(fd, &increment, sizeof(increment));
}

void ShutdownEvent::consume() const {
    uint64_t counter;
    ssize_t bytesRead = read(fd, &counter, sizeof(counter));
    if (bytesRead == -1) {
        throw FileWatcherException("Couldn't read from termination event notifier", errno);
    }
}

Server::Server(JNIEnv* env, jobject watcherCallback)
    : AbstractServer(env, watcherCallback)
    , inotify(new Inotify()) {
    buffer.reserve(EVENT_BUFFER_SIZE);
    jclass listClass = env->FindClass("java/util/List");
    this->listAddMethod = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");
}

void Server::initializeRunLoop() {
}

void Server::shutdownRunLoop() {
    shutdownEvent.trigger();
}

void Server::runLoop() {
    int forever = numeric_limits<int>::max();

    while (!shouldTerminate) {
        processQueues(forever);
    }

    // No need to clean up watch points, they will be cancelled
    // and closed when the Inotify destructs
}

void Server::processQueues(int timeout) {
    struct pollfd fds[2];
    fds[0].fd = shutdownEvent.fd;
    fds[1].fd = inotify->fd;
    fds[0].events = POLLIN;
    fds[1].events = POLLIN;

    int ret = poll(fds, 2, timeout);
    if (ret == -1) {
        throw FileWatcherException("Couldn't poll for events", errno);
    }

    if (IS_SET(fds[0].revents, POLLIN)) {
        shutdownEvent.consume();
        // Ignore counter, we only care about the notification itself
        shouldTerminate = true;
        return;
    }

    if (IS_SET(fds[1].revents, POLLIN)) {
        try {
            handleEvents();
        } catch (const exception& ex) {
            reportFailure(getThreadEnv(), ex);
        }
    }
}

void Server::handleEvents() {
    unsigned int available;
    ioctl(inotify->fd, FIONREAD, &available);

    while (available > 0) {
        ssize_t bytesRead = read(inotify->fd, &buffer[0], buffer.capacity());

        switch (bytesRead) {
            case -1:
                if (errno == EAGAIN) {
                    // For a non-blocking read, we receive EAGAIN here if there is nothing to read.
                    // This may happen when the inotify is already closed.
                    return;
                } else {
                    throw FileWatcherException("Couldn't read from inotify", errno);
                }
                break;
            case 0:
                throw FileWatcherException("EOF reading from inotify", errno);
                break;
            default:
                // Handle events
                unique_lock<recursive_mutex> lock(mutationMutex);
                JNIEnv* env = getThreadEnv();
                logToJava(LogLevel::FINE, "Processing %d bytes worth of events", bytesRead);
                int index = 0;
                int count = 0;
                while (index < bytesRead) {
                    const struct inotify_event* event = (struct inotify_event*) &buffer[index];
                    handleEvent(env, event);
                    index += sizeof(struct inotify_event) + event->len;
                    count++;
                }
                logToJava(LogLevel::FINE, "Processed %d events", count);
                break;
        }
        available -= bytesRead;
    }
}

void Server::handleEvent(JNIEnv* env, const inotify_event* event) {
    uint32_t mask = event->mask;
    const char* eventName = (event->len == 0)
        ? ""
        : event->name;
    logToJava(LogLevel::FINE, "Event mask: 0x%x for %s (wd = %d, cookie = 0x%x, len = %d)", mask, eventName, event->wd, event->cookie, event->len);
    if (IS_SET(mask, IN_UNMOUNT)) {
        return;
    }

    // Overflow received, handle gracefully
    if (IS_SET(mask, IN_Q_OVERFLOW)) {
        for (auto it : watchPoints) {
            auto path = it.first;
            reportOverflow(env, path);
        }
        return;
    }

    auto iWatchRoot = watchRoots.find(event->wd);
    if (iWatchRoot == watchRoots.end()) {
        auto iRecentlyUnregisteredWatchPoint = recentlyUnregisteredWatchRoots.find(event->wd);
        if (iRecentlyUnregisteredWatchPoint == recentlyUnregisteredWatchRoots.end()) {
            logToJava(LogLevel::INFO, "Received event for unknown watch descriptor %d", event->wd);
        } else {
            // We've removed this via unregisterPath() not long ago
            auto& path = iRecentlyUnregisteredWatchPoint->second;
            if (IS_SET(mask, IN_IGNORED)) {
                logToJava(LogLevel::FINE, "Finished watching recently unregistered watch point '%s' (wd = %d)",
                    utf16ToUtf8String(path).c_str(), event->wd);
                recentlyUnregisteredWatchRoots.erase(iRecentlyUnregisteredWatchPoint);
            } else {
                logToJava(LogLevel::FINE, "Ignoring incoming events for recently removed watch descriptor for '%s' (wd = %d)",
                    utf16ToUtf8String(path).c_str(), event->wd);
            }
        }
        return;
    }

    auto path = iWatchRoot->second;
    auto& watchPoint = watchPoints.at(path);

    if (IS_SET(mask, IN_IGNORED)) {
        // Finished with watch point
        logToJava(LogLevel::FINE, "Finished watching still registered '%s' (wd = %d)",
            utf16ToUtf8String(path).c_str(), event->wd);
        watchRoots.erase(event->wd);
        watchPoints.erase(path);
        return;
    }

    if (watchPoint.status != WatchPointStatus::LISTENING) {
        logToJava(LogLevel::FINE, "Ignoring incoming events for %s as watch-point is not listening (status = %d)",
            utf16ToUtf8String(path).c_str(), watchPoint.status);
        return;
    }

    if (shouldTerminate) {
        logToJava(LogLevel::FINE, "Ignoring incoming events for %s because server is terminating (status = %d)",
            utf16ToUtf8String(path).c_str(), watchPoint.status);
        return;
    }

    ChangeType type;
    const u16string name = utf8ToUtf16String(eventName);

    if (!name.empty()) {
        path.append(u"/");
        path.append(name);
    }

    if (IS_SET(mask, IN_CREATE | IN_MOVED_TO)) {
        type = ChangeType::CREATED;
    } else if (IS_SET(mask, IN_DELETE | IN_DELETE_SELF | IN_MOVED_FROM)) {
        type = ChangeType::REMOVED;
    } else if (IS_SET(mask, IN_MODIFY)) {
        type = ChangeType::MODIFIED;
    } else {
        logToJava(LogLevel::WARNING, "Unknown event 0x%x for %s", mask, utf16ToUtf8String(path).c_str());
        reportUnknownEvent(env, path);
        return;
    }

    reportChangeEvent(env, type, path);
}

void Server::registerPaths(const vector<u16string>& paths) {
    unique_lock<recursive_mutex> lock(mutationMutex);
    for (auto& path : paths) {
        registerPath(path);
    }
}

bool Server::unregisterPaths(const vector<u16string>& paths) {
    unique_lock<recursive_mutex> lock(mutationMutex);
    bool success = true;
    for (auto& path : paths) {
        success &= unregisterPath(path);
    }
    return success;
}

void Server::registerPath(const u16string& path) {
    auto it = watchPoints.find(path);
    if (it != watchPoints.end()) {
        throw FileWatcherException("Already watching path", path);
    }
    string pathNarrow = utf16ToUtf8String(path);
    struct stat st;
    if (lstat(pathNarrow.c_str(), &st) != 0) {
        throw FileWatcherException("Couldn't add watch, stat failed", path, errno);
    }

    int watchDescriptor = inotify_add_watch(inotify->fd, pathNarrow.c_str(), EVENT_MASK);
    if (watchDescriptor == -1) {
        if (errno == ENOSPC) {
            rethrowAsJavaException(getThreadEnv(), InotifyWatchesLimitTooLowException(), linuxJniConstants->inotifyWatchesLimitTooLowExceptionClass.get());
            throw JavaExceptionThrownException();
        }
        throw FileWatcherException("Couldn't add watch, inotify_add_watch failed", path, errno);
    }
    if (watchRoots.find(watchDescriptor) != watchRoots.end()) {
        throw FileWatcherException("Already watching path", path);
    }

    watchPoints.emplace(piecewise_construct,
        forward_as_tuple(path),
        forward_as_tuple(path, inotify, watchDescriptor, st.st_ino));
    watchRoots[watchDescriptor] = path;
}

bool Server::unregisterPath(const u16string& path) {
    auto it = watchPoints.find(path);
    if (it == watchPoints.end()) {
        logToJava(LogLevel::INFO, "Path is not watched: %s", utf16ToUtf8String(path).c_str());
        return false;
    }
    auto& watchPoint = it->second;
    int wd = watchPoint.watchDescriptor;
    CancelResult ret = watchPoint.cancel();
    if (ret == CancelResult::ALREADY_CANCELLED) {
        return false;
    }
    recentlyUnregisteredWatchRoots.emplace(wd, path);
    watchRoots.erase(wd);
    // We use the path instead erase(it) here because on Alpine Linux we've seen crashes happen here
    // when inside a Docker container a host-mapped directory is watched. There is no good theory as
    // of this writing why the problem occurs, but not using the iterator here fixes it.
    watchPoints.erase(path);
    return ret == CancelResult::CANCELLED;
}

void Server::stopWatchingMovedPaths(jobjectArray absolutePathsToCheck, jobject droppedPaths) {
    JNIEnv* env = getThreadEnv();
    int count = env->GetArrayLength(absolutePathsToCheck);
    for (int i = 0; i < count; i++) {
        jstring jPathToCheck = reinterpret_cast<jstring>(env->GetObjectArrayElement(absolutePathsToCheck, i));
        auto pathToCheck = javaToUtf16String(env, jPathToCheck);

        auto it = watchPoints.find(pathToCheck);
        if (it == watchPoints.end()) {
            addToList(env, droppedPaths, jPathToCheck);
            continue;
        }
        auto& watchPoint = it->second;
        if (watchPoint.status != WatchPointStatus::LISTENING) {
            addToList(env, droppedPaths, jPathToCheck);
            continue;
        }

        string pathNarrow = utf16ToUtf8String(watchPoint.path);
        struct stat st;
        if (lstat(pathNarrow.c_str(), &st) == 0 && st.st_ino == watchPoint.inode) {
            continue;
        }

        addToList(env, droppedPaths, jPathToCheck);

        watchPoint.cancel();
    }
}

void Server::addToList(JNIEnv* env, jobject jList, jstring jString) {
        env->CallBooleanMethod(jList, listAddMethod, jString);
        getJavaExceptionAndPrintStacktrace(env);
}

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions_startWatcher0(JNIEnv* env, jclass, jobject javaCallback) {
    try {
        return wrapServer(env, new Server(env, javaCallback));
    } catch (const InotifyInstanceLimitTooLowException& e) {
        rethrowAsJavaException(env, e, linuxJniConstants->inotifyInstanceLimitTooLowExceptionClass.get());
        return NULL;
    }
}

JNIEXPORT jboolean JNICALL
Java_net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions_isGlibc0(JNIEnv*, jclass) {
    void* libcLibrary = dlopen("libc.so.6", RTLD_LAZY);
    if (!libcLibrary) {
        return false;
    }
    void* libcVerCheck = dlsym(libcLibrary, "gnu_get_libc_version");
    jboolean isValid = libcVerCheck != NULL;
    dlclose(libcLibrary);
    return isValid;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions_00024LinuxFileWatcher_stopWatchingMovedPaths0(JNIEnv* env, jobject, jobject javaServer, jobjectArray absolutePathsToCheck, jobject jDroppedPaths) {
    try {
        Server* server = (Server*) getServer(env, javaServer);
        server->stopWatchingMovedPaths(absolutePathsToCheck, jDroppedPaths);
    } catch (const exception& e) {
        rethrowAsJavaException(env, e);
    }
}

LinuxJniConstants::LinuxJniConstants(JavaVM* jvm)
    : JniSupport(jvm)
    , inotifyWatchesLimitTooLowExceptionClass(getThreadEnv(), "net/rubygrapefruit/platform/internal/jni/InotifyWatchesLimitTooLowException")
    , inotifyInstanceLimitTooLowExceptionClass(getThreadEnv(), "net/rubygrapefruit/platform/internal/jni/InotifyInstanceLimitTooLowException") {
}
#endif
