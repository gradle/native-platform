#ifdef __linux__

#include <assert.h>
#include <codecvt>
#include <locale>
#include <string>
#include <unistd.h>

#include "linux_fsnotifier.h"

#define EVENT_BUFFER_SIZE 16 * 1024

#define EVENT_MASK (IN_CREATE | IN_DELETE | IN_DELETE_SELF | IN_EXCL_UNLINK | IN_MODIFY | IN_MOVE_SELF | IN_MOVED_FROM | IN_MOVED_TO | IN_ONLYDIR)

static int registerWatchPoint(const u16string& path, shared_ptr<Inotify> inotify) {
    string pathNarrow = utf16ToUtf8String(path);
    int fdWatch = inotify_add_watch(inotify->fd, pathNarrow.c_str(), EVENT_MASK);
    if (fdWatch == -1) {
        throw FileWatcherException("Couldn't add watch", path, errno);
    }
    return fdWatch;
}

WatchPoint::WatchPoint(const u16string& path, shared_ptr<Inotify> inotify)
    : watchDescriptor(registerWatchPoint(path, inotify))
    , inotify(inotify) {
}

WatchPoint::~WatchPoint() {
}

void WatchPoint::close() {
    if (inotify_rm_watch(inotify->fd, watchDescriptor) != 0) {
        fprintf(stderr, "Couldn't stop watching (inotify = %d, watch descriptor = %d), errno = %d\n", inotify->fd, watchDescriptor, errno);
    }
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

Event::Event()
    : fd(eventfd(0, 0)) {
    if (fd == -1) {
        throw FileWatcherException("Couldn't register event source", errno);
    }
}
Event::~Event() {
    close(fd);
}

void Event::trigger() const {
    const uint64_t increment = 1;
    write(fd, &increment, sizeof(increment));
}

void Event::consume() const {
    uint64_t counter;
    ssize_t bytesRead = read(fd, &counter, sizeof(counter));
    if (bytesRead == -1) {
        throw FileWatcherException("Couldn't read from event notifier", errno);
    }
}

Server::Server(JNIEnv* env, jobject watcherCallback)
    : AbstractServer(env, watcherCallback)
    , inotify(new Inotify()) {
    startThread();
}

void Server::terminate() {
    terminated = true;
}

Server::~Server() {
    // Make copy of watch point paths to avoid race conditions
    list<u16string> paths;
    for (auto& watchPoint : watchPoints) {
        paths.push_back(watchPoint.first);
    }
    for (auto& path : paths) {
        executeOnThread(shared_ptr<Command>(new UnregisterPathCommand(path)));
    }
    executeOnThread(shared_ptr<Command>(new TerminateCommand()));

    if (watcherThread.joinable()) {
        watcherThread.join();
    }
}

void Server::runLoop(JNIEnv*, function<void(exception_ptr)> notifyStarted) {
    notifyStarted(nullptr);

    int forever = numeric_limits<int>::max();

    while (!terminated) {
        processQueues(forever);
    }
}

void Server::processQueues(int timeout) {
    struct pollfd fds[2];
    fds[0].fd = processCommandsEvent.fd;
    fds[1].fd = inotify->fd;
    fds[0].events = POLLIN;
    fds[1].events = POLLIN;

    int ret = poll(fds, 2, timeout);
    if (ret == -1) {
        throw FileWatcherException("Couldn't poll for events", errno);
    }

    if (IS_SET(fds[0].revents, POLLIN)) {
        processCommandsEvent.consume();
        // Ignore counter, we only care about the notification itself
        processCommands();
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
    char buffer[EVENT_BUFFER_SIZE]
        __attribute__((aligned(__alignof__(struct inotify_event))));

    ssize_t bytesRead = read(inotify->fd, buffer, EVENT_BUFFER_SIZE);
    if (bytesRead == -1) {
        if (errno == EAGAIN) {
            // For a non-blocking read, we receive EAGAIN here if there is nothing to read.
            // This may happen when the inotify is already closed.
            return;
        } else {
            throw FileWatcherException("Couldn't read from inotify", errno);
        }
    }
    JNIEnv* env = getThreadEnv();

    switch (bytesRead) {
        case -1:
            // TODO EINTR is the normal termination, right?
            log_severe(env, "Failed to fetch change notifications, errno = %d", errno);
            terminated = true;
            break;
        case 0:
            terminated = true;
            break;
        default:
            // Handle events
            int index = 0;
            while (index < bytesRead) {
                const struct inotify_event* event = (struct inotify_event*) &buffer[index];
                handleEvent(env, event);
                index += sizeof(struct inotify_event) + event->len;
            }
            break;
    }
}

void Server::processCommandsOnThread() {
    processCommandsEvent.trigger();
}

void Server::handleEvent(JNIEnv* env, const inotify_event* event) {
    uint32_t mask = event->mask;
    const char* eventName = (event->len == 0)
        ? ""
        : event->name;
    log_fine(env, "Event mask: 0x%x for %s (wd = %d, cookie = 0x%x, len = %d)", mask, eventName, event->wd, event->cookie, event->len);
    if (IS_ANY_SET(mask, IN_UNMOUNT)) {
        return;
    }

    u16string path = watchRoots[event->wd];
    if (path.empty()) {
        throw FileWatcherException("Couldn't find registered path for watch descriptor");
    }

    if (IS_SET(mask, IN_IGNORED)) {
        // Finished with watch point
        log_fine(env, "Finished watching '%s'", utf16ToUtf8String(path).c_str());
        watchPoints.erase(path);
        watchRoots.erase(event->wd);
        return;
    }

    int type;
    const u16string name = utf8ToUtf16String(eventName);
    // TODO How to handle MOVE_SELF?
    if (IS_SET(mask, IN_Q_OVERFLOW)) {
        type = FILE_EVENT_INVALIDATE;
    } else if (IS_ANY_SET(mask, IN_CREATE | IN_MOVED_TO)) {
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

void Server::registerPath(const u16string& path) {
    if (watchPoints.find(path) != watchPoints.end()) {
        throw FileWatcherException("Already watching path", path);
    }
    auto result = watchPoints.emplace(piecewise_construct,
        forward_as_tuple(path),
        forward_as_tuple(path, inotify));
    auto it = result.first;
    watchRoots[it->second.watchDescriptor] = path;
    log_fine(getThreadEnv(), "Registered %s", utf16ToUtf8String(path).c_str());
}

void Server::unregisterPath(const u16string& path) {
    auto it = watchPoints.find(path);
    if (it == watchPoints.end()) {
        log_fine(getThreadEnv(), "Path is not watched: %s", utf16ToUtf8String(path).c_str());
        return;
    }
    it->second.close();
    // Handle IN_IGNORED event
    processQueues(0);
    log_fine(getThreadEnv(), "Unregistered %s", utf16ToUtf8String(path).c_str());
}

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions_startWatcher(JNIEnv* env, jclass, jobject javaCallback) {
    return wrapServer(env, [env, javaCallback]() {
        return new Server(env, javaCallback);
    });
}

#endif
