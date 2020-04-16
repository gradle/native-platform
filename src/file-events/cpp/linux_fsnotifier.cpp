#ifdef __linux__

#include <codecvt>
#include <locale>
#include <string>
#include <sys/ioctl.h>
#include <unistd.h>

#include "linux_fsnotifier.h"

#define EVENT_BUFFER_SIZE (16 * 1024)

#define EVENT_MASK (IN_CREATE | IN_DELETE | IN_DELETE_SELF | IN_EXCL_UNLINK | IN_MODIFY | IN_MOVE_SELF | IN_MOVED_FROM | IN_MOVED_TO | IN_ONLYDIR)

WatchPoint::WatchPoint(const u16string& path, shared_ptr<Inotify> inotify, int watchDescriptor)
    : status(LISTENING)
    , watchDescriptor(watchDescriptor)
    , inotify(inotify)
    , path(path) {
}

bool WatchPoint::cancel() {
    if (status == CANCELLED) {
        return false;
    }
    status = CANCELLED;
    if (inotify_rm_watch(inotify->fd, watchDescriptor) != 0) {
        throw FileWatcherException("Couldn't stop watching", path, errno);
    }
    return true;
}

Inotify::Inotify()
    : fd(inotify_init1(IN_CLOEXEC | IN_NONBLOCK)) {
    if (fd == -1) {
        throw FileWatcherException("Couldn't register inotify handle", errno);
    }
}

Inotify::~Inotify() {
    close(fd);
}

TerminateEvent::TerminateEvent()
    : fd(eventfd(0, 0)) {
    if (fd == -1) {
        throw FileWatcherException("Couldn't register event source", errno);
    }
}
TerminateEvent::~TerminateEvent() {
    close(fd);
}

void TerminateEvent::trigger() const {
    const uint64_t increment = 1;
    write(fd, &increment, sizeof(increment));
}

void TerminateEvent::consume() const {
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
}

void Server::terminateRunLoop() {
    logToJava(FINE, "Terminating", NULL);
    terminateEvent.trigger();
}

Server::~Server() {
    terminate();
}

void Server::initializeRunLoop() {
}

void Server::runLoop() {
    int forever = numeric_limits<int>::max();

    while (!terminated) {
        processQueues(forever);
    }

    // No need to clean up watch points, they will be cancelled
    // and closed when the Inotify destructs
}

void Server::processQueues(int timeout) {
    struct pollfd fds[2];
    fds[0].fd = terminateEvent.fd;
    fds[1].fd = inotify->fd;
    fds[0].events = POLLIN;
    fds[1].events = POLLIN;

    int ret = poll(fds, 2, timeout);
    if (ret == -1) {
        throw FileWatcherException("Couldn't poll for events", errno);
    }

    if (IS_SET(fds[0].revents, POLLIN)) {
        terminateEvent.consume();
        // Ignore counter, we only care about the notification itself
        terminated = true;
        return;
    }

    if (IS_SET(fds[1].revents, POLLIN)) {
        try {
            handleEvents();
        } catch (const exception& ex) {
            reportError(getThreadEnv(), ex);
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
                unique_lock<mutex> lock(mutationMutex);
                JNIEnv* env = getThreadEnv();
                logToJava(FINE, "Processing %d bytes worth of events", bytesRead);
                int index = 0;
                int count = 0;
                while (index < bytesRead) {
                    const struct inotify_event* event = (struct inotify_event*) &buffer[index];
                    handleEvent(env, event);
                    index += sizeof(struct inotify_event) + event->len;
                    count++;
                }
                logToJava(FINE, "Processed %d events", count);
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
    logToJava(FINE, "Event mask: 0x%x for %s (wd = %d, cookie = 0x%x, len = %d)", mask, eventName, event->wd, event->cookie, event->len);
    if (IS_ANY_SET(mask, IN_UNMOUNT)) {
        return;
    }

    // Overflow received, handle gracefully
    if (IS_SET(mask, IN_Q_OVERFLOW)) {
        for (auto it : watchPoints) {
            auto path = it.first;
            reportChange(env, FILE_EVENT_INVALIDATE, path);
        }
        return;
    }

    if (IS_SET(mask, IN_IGNORED) && recentlyRemovedWatchPoints.erase(event->wd)) {
        // We've removed this via unregisterPath() not long ago
        return;
    }

    auto path = watchRoots.at(event->wd);
    auto& watchPoint = watchPoints.at(path);

    if (IS_SET(mask, IN_IGNORED)) {
        // Finished with watch point
        logToJava(FINE, "Finished watching '%s'", utf16ToUtf8String(path).c_str());
        watchRoots.erase(event->wd);
        watchPoints.erase(path);
        return;
    }

    if (watchPoint.status != LISTENING) {
        logToJava(FINE, "Ignoring incoming events for %s as watch-point is not listening (status = %d)",
            utf16ToUtf8String(path).c_str(), watchPoint.status);
        return;
    }

    if (terminated) {
        logToJava(FINE, "Ignoring incoming events for %s because server is terminating (status = %d)",
            utf16ToUtf8String(path).c_str(), watchPoint.status);
        return;
    }

    int type;
    const u16string name = utf8ToUtf16String(eventName);
    // TODO How to handle MOVE_SELF?
    if (IS_ANY_SET(mask, IN_CREATE | IN_MOVED_TO)) {
        type = FILE_EVENT_CREATED;
    } else if (IS_ANY_SET(mask, IN_DELETE | IN_DELETE_SELF | IN_MOVED_FROM)) {
        type = FILE_EVENT_REMOVED;
    } else if (IS_SET(mask, IN_MODIFY)) {
        type = FILE_EVENT_MODIFIED;
    } else {
        type = FILE_EVENT_UNKNOWN;
    }

    if (!name.empty()) {
        path.append(u"/");
        path.append(name);
    }
    reportChange(env, type, path);
}

static int addInotifyWatch(const u16string& path, shared_ptr<Inotify> inotify) {
    string pathNarrow = utf16ToUtf8String(path);
    int fdWatch = inotify_add_watch(inotify->fd, pathNarrow.c_str(), EVENT_MASK);
    if (fdWatch == -1) {
        throw FileWatcherException("Couldn't add watch", path, errno);
    }
    return fdWatch;
}

void Server::registerPath(const u16string& path) {
    auto it = watchPoints.find(path);
    if (it != watchPoints.end()) {
        throw FileWatcherException("Already watching path", path);
    }
    int watchDescriptor = addInotifyWatch(path, inotify);
    if (watchRoots.find(watchDescriptor) != watchRoots.end()) {
        throw FileWatcherException("Already watching path", path);
    }
    auto result = watchPoints.emplace(piecewise_construct,
        forward_as_tuple(path),
        forward_as_tuple(path, inotify, watchDescriptor));
    auto& watchPoint = result.first->second;
    watchRoots[watchPoint.watchDescriptor] = path;
}

bool Server::unregisterPath(const u16string& path) {
    auto it = watchPoints.find(path);
    if (it == watchPoints.end()) {
        logToJava(INFO, "Path is not watched: %s", utf16ToUtf8String(path).c_str());
        return false;
    }
    auto& watchPoint = it->second;
    if (watchPoint.cancel()) {
        recentlyRemovedWatchPoints.emplace(watchPoint.watchDescriptor);
        watchRoots.erase(watchPoint.watchDescriptor);
        watchPoints.erase(path);
    }
    return true;
}

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions_startWatcher0(JNIEnv* env, jclass, jobject javaCallback) {
    return wrapServer(env, new Server(env, javaCallback));
}

#endif
