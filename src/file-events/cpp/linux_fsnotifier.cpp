#ifdef __linux__

#include <assert.h>
#include <codecvt>
#include <locale>
#include <string>
#include <unistd.h>

#include "linux_fsnotifier.h"

#define EVENT_BUFFER_SIZE 16 * 1024

// TODO Should we include IN_DONT_FOLLOW?
// TODO Should we include IN_EXCL_UNLINK?
// TODO Use IN_MASK_CREATE for safety? (only from Linux 4.18)
#define EVENT_MASK (IN_CREATE | IN_DELETE | IN_DELETE_SELF | IN_MODIFY | IN_MOVE_SELF | IN_MOVED_FROM | IN_MOVED_TO | IN_ONLYDIR)

static int registerWatchPoint(const u16string& path, int fdInotify) {
    wstring_convert<codecvt_utf8_utf16<char16_t>, char16_t> conv16;
    string pathNarrow = conv16.to_bytes(path);
    int wd = inotify_add_watch(fdInotify, pathNarrow.c_str(), EVENT_MASK);
    return wd;
}

WatchPoint::WatchPoint(const u16string& path, int fdInotify)
    : watchDescriptor(registerWatchPoint(path, fdInotify))
    , fdInotify(fdInotify) {
}

WatchPoint::~WatchPoint() {
}

void WatchPoint::close() {
    inotify_rm_watch(fdInotify, watchDescriptor);
}

Server::Server(JNIEnv* env, jobject watcherCallback)
    : AbstractServer(env, watcherCallback)
    , fdInotify(inotify_init1(IN_CLOEXEC)) {
    startThread();
}

Server::~Server() {
    terminate = true;

    for (auto& it : watchPoints) {
        it.second.close();
    }

    if (watcherThread.joinable()) {
        watcherThread.join();
    }

    close(fdInotify);
}

void Server::runLoop(JNIEnv* env, function<void(exception_ptr)> notifyStarted) {
    try {
        notifyStarted(nullptr);
    } catch (...) {
        notifyStarted(current_exception());
    }

    char buffer[EVENT_BUFFER_SIZE]
        __attribute__((aligned(__alignof__(struct inotify_event))));

    while (!terminate) {
        log_fine(env, "Waiting for events (fdInotify = 0x%x)", fdInotify);
        ssize_t bytesRead = read(fdInotify, buffer, EVENT_BUFFER_SIZE);
        switch (bytesRead) {
            case -1:
                // TODO EINTR is the normal termination, right?
                log_severe(env, "Failed to fetch change notifications, errno = %d", errno);
                terminate = true;
                break;
            case 0:
                terminate = true;
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
}

void Server::handleEvent(JNIEnv* env, const inotify_event* event) {
    uint32_t mask = event->mask;
    log_fine(env, "Event mask: 0x%x for %s (wd = %d, cookie = 0x%x)", mask, event->name, event->wd, event->cookie);
    if (IS_ANY_SET(mask, IN_UNMOUNT)) {
        return;
    }
    // TODO Do we need error handling here?
    u16string path = watchRoots[event->wd];
    if (IS_SET(mask, IN_IGNORED)) {
        // Finished with watch point
        log_fine(env, "Finished watching", NULL);
        watchPoints.erase(path);
        watchRoots.erase(event->wd);
        return;
    }
    int type;
    const u16string name = utf8ToUtf16String(event->name);
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

void Server::startWatching(const u16string& path) {
    if (watchPoints.find(path) != watchPoints.end()) {
        throw FileWatcherException("Already watching path");
    }
    auto result = watchPoints.emplace(piecewise_construct,
        forward_as_tuple(path),
        forward_as_tuple(path, fdInotify));
    auto it = result.first;
    watchRoots[it->second.watchDescriptor] = path;
}

void Server::stopWatching(const u16string& path) {
    auto it = watchPoints.find(path);
    if (it == watchPoints.end()) {
        throw FileWatcherException("Cannot stop watching path that was never watched");
    }
    it->second.close();
}

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions_startWatcher(JNIEnv* env, jclass, jobject javaCallback) {
    return wrapServer(env, [env, javaCallback]() {
        return new Server(env, javaCallback);
    });
}

#endif
