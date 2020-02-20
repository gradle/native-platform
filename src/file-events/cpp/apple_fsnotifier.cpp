#if defined(__APPLE__)

#include "apple_fsnotifier.h"
#include <codecvt>
#include <locale>
#include <string>

using namespace std;

// Utility wrapper to adapt locale-bound facets for wstring convert
// See https://en.cppreference.com/w/cpp/locale/codecvt
// TODO Understand what this does
template <class Facet>
struct deletable_facet : Facet {
    template <class... Args>
    deletable_facet(Args&&... args)
        : Facet(std::forward<Args>(args)...) {
    }
    ~deletable_facet() {
    }
};

EventStream::EventStream(Server* server, CFRunLoopRef runLoop, CFStringRef path, long latencyInMillis) {
    CFMutableArrayRef pathArray = CFArrayCreateMutable(NULL, 1, NULL);
    if (pathArray == NULL) {
        throw FileWatcherException("Could not allocate array to store roots to watch");
    }
    CFArrayAppendValue(pathArray, path);

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
    if (watcherStream == NULL) {
        throw FileWatcherException("Could not create FSEventStreamCreate to track changes");
    }
    FSEventStreamScheduleWithRunLoop(watcherStream, runLoop, kCFRunLoopDefaultMode);
    FSEventStreamStart(watcherStream);
    this->watcherStream = watcherStream;
}

EventStream::~EventStream() {
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

Server::Server(JNIEnv* env, jobject watcherCallback, jobjectArray rootsToWatch, long latencyInMillis)
    : rootsToWatch(rootsToWatch)
    , latencyInMillis(latencyInMillis)
    , AbstractServer(env, watcherCallback) {
    // TODO Would be nice to inline this in AbstractServer(), but doing so results in pure virtual call
    startThread();
}

Server::~Server() {
    if (threadLoop != NULL) {
        CFRunLoopStop(threadLoop);
    }

    if (watcherThread.joinable()) {
        watcherThread.join();
    }
}

void Server::runLoop(JNIEnv* env, function<void(exception_ptr)> notifyStarted) {
    try {
        CFRunLoopRef threadLoop = CFRunLoopGetCurrent();
        this->threadLoop = threadLoop;

        int count = env->GetArrayLength(rootsToWatch);
        for (int i = 0; i < count; i++) {
            jstring javaPath = (jstring) env->GetObjectArrayElement(rootsToWatch, i);
            jsize javaPathLength = env->GetStringLength(javaPath);
            const jchar* javaPathChars = env->GetStringCritical(javaPath, nullptr);
            if (javaPathChars == NULL) {
                throw FileWatcherException("Could not get Java string character");
            }
            CFStringRef stringPath = CFStringCreateWithCharacters(NULL, javaPathChars, javaPathLength);
            env->ReleaseStringCritical(javaPath, javaPathChars);
            if (stringPath == NULL) {
                throw FileWatcherException("Could not create CFStringRef");
            }
            watchPoints.emplace_back(this, threadLoop, stringPath, latencyInMillis);
        }

        notifyStarted(nullptr);
    } catch (...) {
        notifyStarted(current_exception());
    }

    CFRunLoopRun();
}

static void handleEventsCallback(
    ConstFSEventStreamRef streamRef,
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

    for (int i = 0; i < numEvents; i++) {
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
    // TODO Can we extract this to some static state? It should only be used from the server thread
    wstring_convert<deletable_facet<codecvt<char16_t, char, mbstate_t>>, char16_t> conv16;
    u16string pathStr = conv16.from_bytes(path);
    reportChange(env, type, pathStr);
}

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions_startWatching(JNIEnv* env, jclass target, jobjectArray paths, long latencyInMillis, jobject javaCallback) {
    Server* server;
    try {
        server = new Server(env, javaCallback, paths, latencyInMillis);
    } catch (const exception& e) {
        log_severe(env, "Caught exception: %s", e.what());
        jclass exceptionClass = env->FindClass("net/rubygrapefruit/platform/NativeException");
        assert(exceptionClass != NULL);
        jint ret = env->ThrowNew(exceptionClass, e.what());
        assert(ret == 0);
        return NULL;
    }

    jclass clsWatcher = env->FindClass("net/rubygrapefruit/platform/internal/jni/OsxFileEventFunctions$WatcherImpl");
    assert(clsWatcher != NULL);
    jmethodID constructor = env->GetMethodID(clsWatcher, "<init>", "(Ljava/lang/Object;)V");
    assert(constructor != NULL);
    return env->NewObject(clsWatcher, constructor, env->NewDirectByteBuffer(server, sizeof(server)));
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions_stopWatching(JNIEnv* env, jclass target, jobject detailsObj) {
    Server* server = (Server*) env->GetDirectBufferAddress(detailsObj);
    assert(server != NULL);
    delete server;
}

#endif
