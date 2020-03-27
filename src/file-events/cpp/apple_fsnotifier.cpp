#if defined(__APPLE__)

#include "apple_fsnotifier.h"

using namespace std;

WatchPoint::WatchPoint(Server* server, CFRunLoopRef runLoop, const u16string& path, long latencyInMillis) {
    CFStringRef cfPath = CFStringCreateWithCharacters(NULL, (UniChar*) path.c_str(), path.length());
    if (cfPath == nullptr) {
        throw FileWatcherException("Could not allocate CFString for path", path);
    }
    CFMutableArrayRef pathArray = CFArrayCreateMutable(NULL, 1, NULL);
    if (pathArray == NULL) {
        throw FileWatcherException("Could not allocate array to store root to watch", path);
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
        throw FileWatcherException("Couldn't add watch", path);
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

void processCommandsCallback(void* info) {
    Server* server = (Server*) info;
    server->processCommands();
}

Server::Server(JNIEnv* env, jobject watcherCallback, long latencyInMillis)
    : AbstractServer(env, watcherCallback)
    , latencyInMillis(latencyInMillis) {
    CFRunLoopSourceContext context = {
        0,                         // version;
        (void*) this,              // info;
        NULL,                      // retain()
        NULL,                      // release()
        NULL,                      // copyDescription()
        NULL,                      // equal()
        NULL,                      // hash()
        NULL,                      // schedule()
        NULL,                      // cancel()
        processCommandsCallback    // perform()
    };
    messageSource = CFRunLoopSourceCreate(
        kCFAllocatorDefault,    // allocator
        0,                      // index
        &context                // context
    );
    startThread();
}

Server::~Server() {
    vector<u16string> paths;
    paths.reserve(watchPoints.size());
    for (auto& it : watchPoints) {
        auto& path = it.first;
        paths.push_back(path);
    }
    executeOnThread(shared_ptr<Command>(new UnregisterPathsCommand(paths)));
    executeOnThread(shared_ptr<Command>(new TerminateCommand()));

    if (watcherThread.joinable()) {
        watcherThread.join();
    }
    CFRelease(messageSource);
}

void Server::runLoop(function<void(exception_ptr)> notifyStarted) {
    try {
        CFRunLoopRef threadLoop = CFRunLoopGetCurrent();
        this->threadLoop = threadLoop;

        CFRunLoopAddSource(threadLoop, messageSource, kCFRunLoopDefaultMode);

        notifyStarted(nullptr);
    } catch (...) {
        notifyStarted(current_exception());
    }

    CFRunLoopRun();
}

void Server::processCommandsOnThread() {
    CFRunLoopSourceSignal(messageSource);
    CFRunLoopWakeUp(threadLoop);
}

void Server::terminate() {
    CFRunLoopStop(threadLoop);
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

    try {
        for (size_t i = 0; i < numEvents; i++) {
            handleEvent(env, eventPaths[i], eventFlags[i]);
        }
    } catch (const exception& ex) {
        reportError(env, ex);
    }
}

void Server::handleEvent(JNIEnv* env, char* path, FSEventStreamEventFlags flags) {
    logToJava(FINE, "Event flags: 0x%x for %s", flags, path);

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
        logToJava(WARNING, "Unknown event 0x%x for %s", flags, path);
        type = FILE_EVENT_UNKNOWN;
    }

    logToJava(FINE, "Changed: %s %d", path, type);
    u16string pathStr = utf8ToUtf16String(path);
    reportChange(env, type, pathStr);
}

void Server::registerPath(const u16string& path) {
    if (watchPoints.find(path) != watchPoints.end()) {
        throw FileWatcherException("Already watching path", path);
    }
    watchPoints.emplace(piecewise_construct,
        forward_as_tuple(path),
        forward_as_tuple(this, threadLoop, path, latencyInMillis));
}

void Server::unregisterPath(const u16string& path) {
    if (watchPoints.erase(path) == 0) {
        logToJava(WARNING, "Path is not watched: %s", utf16ToUtf8String(path).c_str());
    }
}

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions_startWatcher0(JNIEnv* env, jclass, long latencyInMillis, jobject javaCallback) {
    return wrapServer(env, [env, javaCallback, latencyInMillis]() {
        return new Server(env, javaCallback, latencyInMillis);
    });
}

#endif
