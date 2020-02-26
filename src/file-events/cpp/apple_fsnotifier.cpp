#if defined(__APPLE__)

#include "apple_fsnotifier.h"
#include <codecvt>
#include <locale>
#include <string>

using namespace std;

// Utility wrapper to adapt locale-bound facets for wstring convert
// Exposes the protected destructor as public
// See https://en.cppreference.com/w/cpp/locale/codecvt
template <class Facet>
struct deletable_facet : Facet {
    template <class... Args>
    deletable_facet(Args&&... args)
        : Facet(forward<Args>(args)...) {
    }
    ~deletable_facet() {
    }
};

WatchPoint::WatchPoint(Server* server, CFRunLoopRef runLoop, const u16string& path, long latencyInMillis) {
    CFStringRef cfPath = CFStringCreateWithCharacters(NULL, (UniChar*) path.c_str(), path.length());
    if (cfPath == nullptr) {
        throw FileWatcherException("Could not allocate CFString for path");
    }
    CFMutableArrayRef pathArray = CFArrayCreateMutable(NULL, 1, NULL);
    if (pathArray == NULL) {
        throw FileWatcherException("Could not allocate array to store root to watch");
    }
    CFArrayAppendValue(pathArray, cfPath);

    FSEventStreamContext context = {
        0,                 // version, must be 0
        (void*) server,    // info
        NULL,              // retain
        NULL,              // release
        NULL               // copyDescription
    };
    FSEventStreamRef watcherStream = FSEventStreamCreate(
        NULL,
        &handleEventsCallback,
        &context,
        pathArray,
        kFSEventStreamEventIdSinceNow,
        latencyInMillis / 1000.0,
        kFSEventStreamCreateFlagNoDefer | kFSEventStreamCreateFlagFileEvents | kFSEventStreamCreateFlagWatchRoot);
    CFRelease(pathArray);
    CFRelease(cfPath);
    if (watcherStream == NULL) {
        throw FileWatcherException("Could not create FSEventStreamCreate to track changes");
    }
    FSEventStreamScheduleWithRunLoop(watcherStream, runLoop, kCFRunLoopDefaultMode);
    FSEventStreamStart(watcherStream);
    this->watcherStream = watcherStream;
}

WatchPoint::~WatchPoint() {
    // Reading the Apple docs it seems we should call FSEventStreamFlushSync() here.
    // But doing so produces this log:
    //
    //     2020-02-17 23:02 java[50430] (FSEvents.framework) FSEventStreamFlushSync(): failed assertion '(SInt64)last_id > 0LL'
    //
    // According to this comment we should not use flush at all, and it's probably broken:
    // https://github.com/nodejs/node/issues/854#issuecomment-294892950
    // As the comment mentions, even Watchman doesn't flush:
    // https://github.com/facebook/watchman/blob/b397e00cf566f361282a456122eef4e909f26182/watcher/fsevents.cpp#L276-L285
    // FSEventStreamFlushSync(watcherStream);
    FSEventStreamStop(watcherStream);
    FSEventStreamInvalidate(watcherStream);
    FSEventStreamRelease(watcherStream);
}

//
// Server
//

Server::Server(JNIEnv* env, jobject watcherCallback, long latencyInMillis)
    : AbstractServer(env, watcherCallback)
    , latencyInMillis(latencyInMillis) {
    startThread();
}

Server::~Server() {
    watchPoints.clear();

    if (threadLoop != NULL) {
        if (keepAlive != NULL) {
            CFRunLoopRemoveTimer(threadLoop, keepAlive, kCFRunLoopDefaultMode);
            CFRelease(keepAlive);
        }
        if (CFRunLoopIsWaiting(threadLoop)) {
            CFRunLoopStop(threadLoop);
        }
    }

    if (watcherThread.joinable()) {
        watcherThread.join();
    }
}

void Server::runLoop(JNIEnv*, function<void(exception_ptr)> notifyStarted) {
    try {
        CFRunLoopRef threadLoop = CFRunLoopGetCurrent();
        this->threadLoop = threadLoop;

        // Make sure we have at least one source for our run loop, otherwise it would exit immediately
        CFAbsoluteTime forever = numeric_limits<double>::max();
        keepAlive = CFRunLoopTimerCreate(
            kCFAllocatorDefault,    // allocator
            forever,                // fireDate
            0,                      // interval
            0,                      // flags, must be 0
            0,                      // order, must be 0
            NULL,                   // callout
            NULL                    // context
        );
        CFRunLoopAddTimer(threadLoop, keepAlive, kCFRunLoopDefaultMode);

        notifyStarted(nullptr);
    } catch (...) {
        notifyStarted(current_exception());
    }

    CFRunLoopRun();
}

static void handleEventsCallback(
    ConstFSEventStreamRef,
    void* clientCallBackInfo,
    size_t numEvents,
    void* eventPaths,
    const FSEventStreamEventFlags eventFlags[],
    const FSEventStreamEventId*) {
    Server* server = (Server*) clientCallBackInfo;
    server->handleEvents(numEvents, (char**) eventPaths, eventFlags);
}

void Server::handleEvents(
    size_t numEvents,
    char** eventPaths,
    const FSEventStreamEventFlags eventFlags[]) {
    JNIEnv* env = getThreadEnv();

    for (size_t i = 0; i < numEvents; i++) {
        handleEvent(env, eventPaths[i], eventFlags[i]);
    }
}

void Server::handleEvent(JNIEnv* env, char* path, FSEventStreamEventFlags flags) {
    log_fine(env, "Event flags: 0x%x for %s", flags, path);

    jint type;
    if (IS_SET(flags, kFSEventStreamEventFlagHistoryDone)) {
        return;
    } else if (IS_ANY_SET(flags,
                   kFSEventStreamEventFlagRootChanged
                       | kFSEventStreamEventFlagMount
                       | kFSEventStreamEventFlagUnmount
                       | kFSEventStreamEventFlagMustScanSubDirs)) {
        type = FILE_EVENT_INVALIDATE;
    } else if (IS_SET(flags, kFSEventStreamEventFlagItemRenamed)) {
        if (IS_SET(flags, kFSEventStreamEventFlagItemCreated)) {
            type = FILE_EVENT_REMOVED;
        } else {
            type = FILE_EVENT_CREATED;
        }
    } else if (IS_SET(flags, kFSEventStreamEventFlagItemModified)) {
        type = FILE_EVENT_MODIFIED;
    } else if (IS_SET(flags, kFSEventStreamEventFlagItemRemoved)) {
        type = FILE_EVENT_REMOVED;
    } else if (IS_ANY_SET(flags,
                   kFSEventStreamEventFlagItemInodeMetaMod    // file locked
                       | kFSEventStreamEventFlagItemFinderInfoMod
                       | kFSEventStreamEventFlagItemChangeOwner
                       | kFSEventStreamEventFlagItemXattrMod)) {
        type = FILE_EVENT_MODIFIED;
    } else if (IS_SET(flags, kFSEventStreamEventFlagItemCreated)) {
        type = FILE_EVENT_CREATED;
    } else {
        log_warning(env, "Unknown event 0x%x for %s", flags, path);
        type = FILE_EVENT_UNKNOWN;
    }

    log_fine(env, "Changed: %s %d", path, type);
    wstring_convert<deletable_facet<codecvt<char16_t, char, mbstate_t>>, char16_t> conv16;
    u16string pathStr = conv16.from_bytes(path);
    reportChange(env, type, pathStr);
}

void Server::startWatching(const u16string& path) {
    if (watchPoints.find(path) != watchPoints.end()) {
        throw FileWatcherException("Already watching path");
    }
    watchPoints.emplace(piecewise_construct,
        forward_as_tuple(path),
        forward_as_tuple(this, threadLoop, path, latencyInMillis));
}

void Server::stopWatching(const u16string& path) {
    if (watchPoints.erase(path) == 0) {
        throw FileWatcherException("Cannot stop watching path that was never watched");
    }
}

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions_startWatcher(JNIEnv* env, jclass, long latencyInMillis, jobject javaCallback) {
    Server* server;
    try {
        server = new Server(env, javaCallback, latencyInMillis);
    } catch (const exception& e) {
        log_severe(env, "Caught exception: %s", e.what());
        jclass exceptionClass = env->FindClass("net/rubygrapefruit/platform/NativeException");
        assert(exceptionClass != NULL);
        jint ret = env->ThrowNew(exceptionClass, e.what());
        assert(ret == 0);
        return NULL;
    }

    jclass clsWatcher = env->FindClass("net/rubygrapefruit/platform/internal/jni/AbstractFileEventFunctions$NativeFileWatcher");
    assert(clsWatcher != NULL);
    jmethodID constructor = env->GetMethodID(clsWatcher, "<init>", "(Ljava/lang/Object;)V");
    assert(constructor != NULL);
    return env->NewObject(clsWatcher, constructor, env->NewDirectByteBuffer(server, sizeof(server)));
}

#endif
