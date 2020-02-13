#if defined(__APPLE__)

#include "net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions.h"
#include "generic.h"
#include <CoreServices/CoreServices.h>
#include <thread>

using namespace std;

class Server;

static void handleEventsCallback(
    ConstFSEventStreamRef streamRef,
    void *clientCallBackInfo,
    size_t numEvents,
    void *eventPaths,
    const FSEventStreamEventFlags eventFlags[],
    const FSEventStreamEventId eventIds[]);

class Server {
public:
    Server(JNIEnv *env, jobject watcherCallback, CFArrayRef rootsToWatch, long latencyInMillis);
    ~Server();

private:
    void run();

    void handleEvents(
        size_t numEvents,
        char **eventPaths,
        const FSEventStreamEventFlags eventFlags[],
        const FSEventStreamEventId eventIds[]);
    friend void handleEventsCallback(
        ConstFSEventStreamRef streamRef,
        void *clientCallBackInfo,
        size_t numEvents,
        void *eventPaths,
        const FSEventStreamEventFlags eventFlags[],
        const FSEventStreamEventId eventIds[]);

    void handleEvent(JNIEnv *env, char* path, FSEventStreamEventFlags flags);

    // TODO: Move this to somewhere else
    JNIEnv* getThreadEnv();

    JavaVM *jvm;
    jobject watcherCallback;
    jmethodID watcherCallbackMethod;

    FSEventStreamRef watcherStream;
    thread watcherThread;
    CFRunLoopRef threadLoop;
    bool invalidStateDetected;
};

Server::Server(JNIEnv *env, jobject watcherCallback, CFArrayRef rootsToWatch, long latencyInMillis) {
    JavaVM* jvm;
    int jvmStatus = env->GetJavaVM(&jvm);
    if (jvmStatus < 0) {
        log_severe(env, "Could not store jvm instance", NULL);
        return;
    }

    this->jvm = jvm;
    // TODO Handle if returns NULL
    this->watcherCallback = env->NewGlobalRef(watcherCallback);
    jclass callbackClass = env->GetObjectClass(watcherCallback);
    this->watcherCallbackMethod = env->GetMethodID(callbackClass, "pathChanged", "(ILjava/lang/String;)V");

    this->invalidStateDetected = false;

    FSEventStreamContext context = {
        0,              // version, must be 0
        (void*) this,   // info
        NULL,           // retain
        NULL,           // release
        NULL            // copyDescription
    };
    FSEventStreamRef watcherStream = FSEventStreamCreate(
        NULL,
        &handleEventsCallback,
        &context,
        rootsToWatch,
        kFSEventStreamEventIdSinceNow,
        latencyInMillis / 1000.0,
        kFSEventStreamCreateFlagNoDefer | kFSEventStreamCreateFlagFileEvents | kFSEventStreamCreateFlagWatchRoot);
    if (watcherStream == NULL) {
        log_severe(env, "Could not create FSEventStreamCreate to track changes", NULL);
        // TODO Error handling
        return;
    }
    this->watcherStream = watcherStream;
    this->watcherThread = thread(&Server::run, this);
}

Server::~Server() {
    JNIEnv *env = getThreadEnv();

    if (invalidStateDetected) {
        // report and reset flag, but try to clean up state as much as possible
        log_severe(env, "Watcher is in invalid state, reported changes may be incorrect", NULL);
    }

    if (threadLoop != NULL) {
        CFRunLoopStop(threadLoop);
    }

    watcherThread.join();

    if (watcherStream != NULL) {
        FSEventStreamRelease(watcherStream);
    }

    if (watcherCallback != NULL) {
        env->DeleteGlobalRef(watcherCallback);
    }
}

void Server::run() {
    JNIEnv* env = attach_jni(jvm, true);

    log_fine(env, "Starting thread", NULL);

    CFRunLoopRef threadLoop = CFRunLoopGetCurrent();
    FSEventStreamScheduleWithRunLoop(watcherStream, threadLoop, kCFRunLoopDefaultMode);
    FSEventStreamStart(watcherStream);
    this->threadLoop = threadLoop;

    CFRunLoopRun();

    FSEventStreamFlushSync(watcherStream);
    FSEventStreamStop(watcherStream);
    FSEventStreamInvalidate(watcherStream);

    log_fine(env, "Stopping thread", NULL);

    detach_jni(jvm);
}

static void handleEventsCallback(
    ConstFSEventStreamRef streamRef,
    void *clientCallBackInfo,
    size_t numEvents,
    void *eventPaths,
    const FSEventStreamEventFlags eventFlags[],
    const FSEventStreamEventId eventIds[]
) {
    Server *server = (Server*) clientCallBackInfo;
    server->handleEvents(numEvents, (char **) eventPaths, eventFlags, eventIds);
}

void Server::handleEvents(
    size_t numEvents,
    char **eventPaths,
    const FSEventStreamEventFlags eventFlags[],
    const FSEventStreamEventId eventIds[]
) {
    if (invalidStateDetected) {
        // TODO Handle this better
        return;
    }

    JNIEnv* env = getThreadEnv();

    for (int i = 0; i < numEvents; i++) {
        handleEvent(env, eventPaths[i], eventFlags[i]);
    }
}

void Server::handleEvent(JNIEnv *env, char* path, FSEventStreamEventFlags flags) {
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
            kFSEventStreamEventFlagItemInodeMetaMod // file locked
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

    env->CallVoidMethod(watcherCallback, watcherCallbackMethod, type, env->NewStringUTF(path));
}

static JNIEnv* lookupThreadEnv(JavaVM *jvm) {
    JNIEnv* env;
    // TODO Verify that JNI 1.6 is the right version
    jint ret = jvm->GetEnv((void **) &(env), JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        fprintf(stderr, "Failed to get JNI env for current thread: %d\n", ret);
        return NULL;
    }
    return env;
}

JNIEnv* Server::getThreadEnv() {
    return lookupThreadEnv(jvm);
}

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions_startWatching(JNIEnv *env, jclass target, jobjectArray paths, long latencyInMillis, jobject javaCallback, jobject result) {

    log_fine(env, "Configuring...", NULL);

    int count = env->GetArrayLength(paths);
    if (count == 0) {
        log_severe(env, "No paths given to watch", NULL);
        // TODO Error handling
        return NULL;
    }

    CFMutableArrayRef rootsToWatch = CFArrayCreateMutable(NULL, count, NULL);
    if (rootsToWatch == NULL) {
        log_severe(env, "Could not allocate array to store roots to watch", NULL);
        // TODO Error handling
        return NULL;
    }

    for (int i = 0; i < count; i++) {
        jstring path = (jstring) env->GetObjectArrayElement(paths, i);
        char* watchedPath = java_to_char(env, path, result);
        log_fine(env, "Watching %s", watchedPath);
        if (watchedPath == NULL) {
            log_severe(env, "Could not allocate string to store root to watch.", NULL);
            // TODO Free resources
            return NULL;
        }
        CFStringRef stringPath = CFStringCreateWithCString(NULL, watchedPath, kCFStringEncodingUTF8);
        free(watchedPath);
        if (stringPath == NULL) {
            log_severe(env, "Could not create CFStringRef", NULL);
            // TODO Free resources
            return NULL;
        }
        CFArrayAppendValue(rootsToWatch, stringPath);
    }

    Server* server = new Server(env, javaCallback, rootsToWatch, latencyInMillis);

    CFRelease(rootsToWatch);

    jclass clsWatcher = env->FindClass("net/rubygrapefruit/platform/internal/jni/OsxFileEventFunctions$WatcherImpl");
    jmethodID constructor = env->GetMethodID(clsWatcher, "<init>", "(Ljava/lang/Object;)V");
    return env->NewObject(clsWatcher, constructor, env->NewDirectByteBuffer(server, sizeof(server)));
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions_stopWatching(JNIEnv *env, jclass target, jobject detailsObj, jobject result) {
    Server *server = (Server*) env->GetDirectBufferAddress(detailsObj);
    delete server;
}

#endif
