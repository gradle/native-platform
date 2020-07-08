#if defined(__APPLE__)

#include "apple_fsnotifier.h"

using namespace std;

void Server::createEventStream() {
    // Do not try to create an event stream if there's nothing to watch
    if (watchPoints.empty()) {
        return;
    }

    CFMutableArrayRef pathArray = CFArrayCreateMutable(NULL, watchPoints.size(), NULL);
    if (pathArray == NULL) {
        throw FileWatcherException("Could not allocate array to store roots to watch");
    }

    for (auto& path : watchPoints) {
        CFStringRef cfPath = CFStringCreateWithCharacters(NULL, (UniChar*) path.c_str(), path.length());
        if (cfPath == nullptr) {
            throw FileWatcherException("Could not allocate CFString for path", path);
        }
        CFArrayAppendValue(pathArray, cfPath);
        // Do not release path separately as it causes a segmentation fault
        // CFRelease(cfPath);
    }

    FSEventStreamContext context = {
        0,               // version, must be 0
        (void*) this,    // info
        NULL,            // retain
        NULL,            // release
        NULL             // copyDescription
    };
    logToJava(LogLevel::FINE, "Starting stream from %d", lastSeenEventId);
    FSEventStreamRef eventStream = FSEventStreamCreate(
        NULL,
        &handleEventsCallback,
        &context,
        pathArray,
        lastSeenEventId,
        latencyInMillis / 1000.0,
        kFSEventStreamCreateFlagNoDefer | kFSEventStreamCreateFlagFileEvents | kFSEventStreamCreateFlagWatchRoot);
    CFRelease(pathArray);
    if (eventStream == NULL) {
        throw FileWatcherException("Couldn't update event stream");
    }
    FSEventStreamScheduleWithRunLoop(eventStream, threadLoop, kCFRunLoopDefaultMode);
    FSEventStreamStart(eventStream);
    this->eventStream = eventStream;
}

void Server::closeEventStream() {
    if (eventStream == nullptr) {
        return;
    }

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
    FSEventStreamStop(eventStream);
    FSEventStreamInvalidate(eventStream);
    FSEventStreamRelease(eventStream);
    eventStream = nullptr;
}

//
// Server
//

void acceptTrigger(void* info) {
    Server *server = (Server*) info;
    server->handleCommands();
}

void Server::handleCommands() {
    unique_lock<mutex> lock(commandMutex);
    while (!commands.empty()) {
        auto command = commands.front();
        commands.pop();
        executeCommand(command);
    }
}

void Server::queueOnRunLoop(Command* command) {
    unique_lock<mutex> lock(commandMutex);
    commands.push(command);
    CFRunLoopSourceSignal(messageSource);
    CFRunLoopWakeUp(threadLoop);
}

Server::Server(JNIEnv* env, jobject watcherCallback, long latencyInMillis, long commandTimeoutInMillis)
    : AbstractServer(env, watcherCallback)
    , latencyInMillis(latencyInMillis)
    , commandTimeoutInMillis(commandTimeoutInMillis) {
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

    unique_lock<mutex> lock(mutationMutex);
    closeEventStream();
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
        FSEventStreamEventId currentEventId = lastSeenEventId;
        for (size_t i = 0; i < numEvents; i++) {
            handleEvent(env, eventPaths[i], eventFlags[i]);
            currentEventId = eventIds[numEvents - 1];
        }

        unique_lock<mutex> lock(mutationMutex);
        lastSeenEventId = currentEventId;
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

void Server::handleEvent(JNIEnv* env, char* path, FSEventStreamEventFlags flags) {
    FSEventStreamEventFlags normalizedFlags = flags & ~IGNORED_FLAGS;
    logToJava(LogLevel::FINE, "Event flags: 0x%x (normalized: 0x%x) for '%s'", flags, normalizedFlags, path);

    u16string pathStr = utf8ToUtf16String(path);

    if (normalizedFlags == kFSEventStreamCreateFlagNone) {
        logToJava(LogLevel::FINE, "Ignoring event 0x%x for %s", flags, path);
        return;
    }

    if (IS_SET(normalizedFlags, kFSEventStreamEventFlagMustScanSubDirs)) {
        reportOverflow(env, pathStr);
        return;
    }

    ChangeType type;
    if (IS_SET(normalizedFlags,
            kFSEventStreamEventFlagRootChanged
                | kFSEventStreamEventFlagMount
                | kFSEventStreamEventFlagUnmount)) {
        type = ChangeType::INVALIDATED;
    } else if (IS_SET(normalizedFlags, kFSEventStreamEventFlagItemRenamed)) {
        if (IS_SET(normalizedFlags, kFSEventStreamEventFlagItemCreated)) {
            type = ChangeType::REMOVED;
        } else {
            type = ChangeType::CREATED;
        }
    } else if (IS_SET(normalizedFlags, kFSEventStreamEventFlagItemModified)) {
        type = ChangeType::MODIFIED;
    } else if (IS_SET(normalizedFlags, kFSEventStreamEventFlagItemRemoved)) {
        type = ChangeType::REMOVED;
    } else if (IS_SET(normalizedFlags,
                   kFSEventStreamEventFlagItemInodeMetaMod    // file locked
                       | kFSEventStreamEventFlagItemFinderInfoMod
                       | kFSEventStreamEventFlagItemChangeOwner
                       | kFSEventStreamEventFlagItemXattrMod)) {
        type = ChangeType::MODIFIED;
    } else if (IS_SET(normalizedFlags, kFSEventStreamEventFlagItemCreated)) {
        type = ChangeType::CREATED;
    } else {
        logToJava(LogLevel::WARNING, "Unknown event 0x%x (normalized: 0x%x) for %s", flags, normalizedFlags, path);
        reportUnknownEvent(env, pathStr);
        return;
    }

    reportChangeEvent(env, type, pathStr);
}

void Server::registerPathsInternal(const vector<u16string>& paths) {
    executeOnRunLoop(commandTimeoutInMillis, [this, paths]() {
        for (auto& path : paths) {
            if (watchPoints.find(path) != watchPoints.end()) {
                throw FileWatcherException("Already watching path", path);
            }
            watchPoints.emplace(path);
        }
        updateEventStream();
        return true;
    });
}

bool Server::unregisterPathsInternal(const vector<u16string>& paths) {
    return executeOnRunLoop(commandTimeoutInMillis, [this, paths]() {
        bool success = true;
        for (auto& path : paths) {
            if (watchPoints.erase(path) == 0) {
                logToJava(LogLevel::INFO, "Path is not watched: %s", utf16ToUtf8String(path).c_str());
                success = false;
            }
        }
        updateEventStream();
        return success;
    });
}

void Server::updateEventStream() {
    logToJava(LogLevel::FINE, "Updating watchers", NULL);
    closeEventStream();
    createEventStream();
    logToJava(LogLevel::FINE, "Finished updating watchers", NULL);
}

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions_startWatcher0(JNIEnv* env, jclass, long latencyInMillis, long commandTimeoutInMillis, jobject javaCallback) {
    return wrapServer(env, new Server(env, javaCallback, latencyInMillis, commandTimeoutInMillis));
}

#endif
