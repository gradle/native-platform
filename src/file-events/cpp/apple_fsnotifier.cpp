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

void acceptTrigger(void*) {
    // This does nothing, we just need a message source to keep the
    // run loop alive when there are no watch points registered
}

Server::Server(JNIEnv* env, jobject watcherCallback, long latencyInMillis)
    : AbstractServer(env, watcherCallback)
    , latencyInMillis(latencyInMillis) {
    CFRunLoopSourceContext context = {
        0,               // version;
        (void*) this,    // info;
        NULL,            // retain()
        NULL,            // release()
        NULL,            // copyDescription()
        NULL,            // equal()
        NULL,            // hash()
        NULL,            // schedule()
        NULL,            // cancel()
        acceptTrigger    // perform()
    };
    messageSource = CFRunLoopSourceCreate(
        kCFAllocatorDefault,    // allocator
        0,                      // index
        &context                // context
    );
}

void Server::initializeRunLoop() {
    threadLoop = CFRunLoopGetCurrent();
    CFRunLoopAddSource(threadLoop, messageSource, kCFRunLoopDefaultMode);
}

void Server::runLoop() {
    CFRunLoopRun();

    unique_lock<recursive_mutex> lock(mutationMutex);
    watchPoints.clear();
    CFRelease(messageSource);
}

void Server::shutdownRunLoop() {
    CFRunLoopStop(threadLoop);
}

static void handleEventsCallback(
    ConstFSEventStreamRef,
    void* clientCallBackInfo,
    size_t numEvents,
    void* eventPaths,
    const FSEventStreamEventFlags eventFlags[],
    const FSEventStreamEventId eventIds[]) {
    Server* server = (Server*) clientCallBackInfo;
    server->handleEvents(numEvents, (char**) eventPaths, eventFlags, eventIds);
}

void Server::handleEvents(
    size_t numEvents,
    char** eventPaths,
    const FSEventStreamEventFlags eventFlags[],
    const FSEventStreamEventId eventIds[]) {
    JNIEnv* env = getThreadEnv();

    try {
        for (size_t i = 0; i < numEvents; i++) {
            handleEvent(env, eventPaths[i], eventFlags[i], eventIds[i]);
        }
    } catch (const exception& ex) {
        reportFailure(env, ex);
    }
}

/**
 * List of events ignored by our implementation.
 * Anything not ignored here should be handled.
 * If macOS later adds more flags, we'll report those as unknown events this way.
 */
static const FSEventStreamEventFlags IGNORED_FLAGS = kFSEventStreamCreateFlagNone
    // | kFSEventStreamEventFlagMustScanSubDirs
    | kFSEventStreamEventFlagUserDropped
    | kFSEventStreamEventFlagKernelDropped
    | kFSEventStreamEventFlagEventIdsWrapped
    | kFSEventStreamEventFlagHistoryDone
    // | kFSEventStreamEventFlagRootChanged
    // | kFSEventStreamEventFlagMount
    // | kFSEventStreamEventFlagUnmount
    // | kFSEventStreamEventFlagItemCreated
    // | kFSEventStreamEventFlagItemRemoved
    // | kFSEventStreamEventFlagItemInodeMetaMod
    // | kFSEventStreamEventFlagItemRenamed
    // | kFSEventStreamEventFlagItemModified
    // | kFSEventStreamEventFlagItemFinderInfoMod
    // | kFSEventStreamEventFlagItemChangeOwner
    // | kFSEventStreamEventFlagItemXattrMod
    | kFSEventStreamEventFlagItemIsFile
    | kFSEventStreamEventFlagItemIsDir
    | kFSEventStreamEventFlagItemIsSymlink
    | kFSEventStreamEventFlagOwnEvent
    | kFSEventStreamEventFlagItemIsHardlink
    | kFSEventStreamEventFlagItemIsLastHardlink
    | kFSEventStreamEventFlagItemCloned;

void Server::handleEvent(JNIEnv* env, char* path, FSEventStreamEventFlags flags, FSEventStreamEventId eventId) {
    logToJava(LogLevel::FINE, "Event flags: 0x%x (ID %d) for '%s'", flags, eventId, path);

    u16string pathStr = utf8ToUtf16String(path);

    if ((flags & ~IGNORED_FLAGS) == kFSEventStreamCreateFlagNone) {
        logToJava(LogLevel::FINE, "Ignoring event 0x%x (ID %d) for '%s'", flags, eventId, path);
        return;
    }

    if (IS_SET(flags, kFSEventStreamEventFlagMustScanSubDirs)) {
        reportOverflow(env, pathStr);
        return;
    }

    ChangeType type;
    if (IS_SET(flags,
            kFSEventStreamEventFlagRootChanged
                | kFSEventStreamEventFlagMount
                | kFSEventStreamEventFlagUnmount)) {
        type = ChangeType::INVALIDATED;
    } else if (IS_SET(flags, kFSEventStreamEventFlagItemRenamed)) {
        if (IS_SET(flags, kFSEventStreamEventFlagItemCreated)) {
            type = ChangeType::REMOVED;
        } else {
            type = ChangeType::CREATED;
        }
    } else if (IS_SET(flags, kFSEventStreamEventFlagItemModified)) {
        type = ChangeType::MODIFIED;
    } else if (IS_SET(flags, kFSEventStreamEventFlagItemRemoved)) {
        type = ChangeType::REMOVED;
    } else if (IS_SET(flags,
                   kFSEventStreamEventFlagItemInodeMetaMod    // file locked
                       | kFSEventStreamEventFlagItemFinderInfoMod
                       | kFSEventStreamEventFlagItemChangeOwner
                       | kFSEventStreamEventFlagItemXattrMod)) {
        type = ChangeType::MODIFIED;
    } else if (IS_SET(flags, kFSEventStreamEventFlagItemCreated)) {
        type = ChangeType::CREATED;
    } else {
        logToJava(LogLevel::WARNING, "Unknown event 0x%x (ID %d) for '%s'", flags, eventId, path);
        reportUnknownEvent(env, pathStr);
        return;
    }

    reportChangeEvent(env, type, pathStr);
}

void Server::registerPaths(const vector<u16string>& paths) {
    unique_lock<recursive_mutex> lock(mutationMutex);
    for (auto& path : paths) {
        if (watchPoints.find(path) != watchPoints.end()) {
            throw FileWatcherException("Already watching path", path);
        }
        watchPoints.emplace(piecewise_construct,
            forward_as_tuple(path),
            forward_as_tuple(this, threadLoop, path, latencyInMillis));
    }
}

bool Server::unregisterPaths(const vector<u16string>& paths) {
    unique_lock<recursive_mutex> lock(mutationMutex);
    bool success = true;
    for (auto& path : paths) {
        if (watchPoints.erase(path) == 0) {
            logToJava(LogLevel::INFO, "Path is not watched: %s", utf16ToUtf8String(path).c_str());
            success = false;
        }
    }
    return success;
}

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions_startWatcher0(JNIEnv* env, jclass, long latencyInMillis, jobject javaCallback) {
    return wrapServer(env, new Server(env, javaCallback, latencyInMillis));
}

#endif
