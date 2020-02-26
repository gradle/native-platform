#ifdef __linux__

#include <assert.h>
#include <codecvt>
#include <locale>
#include <string>
#include <unistd.h>

#include "linux_fsnotifier.h"

// TODO What's a good number for this?
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
    inotify_rm_watch(fdInotify, watchDescriptor);
}

Server::Server(JNIEnv* env, jobject watcherCallback)
    : AbstractServer(env, watcherCallback)
    , fdInotify(inotify_init1(IN_CLOEXEC)) {
    startThread();
}

Server::~Server() {
    watchPoints.clear();

    if (watcherThread.joinable()) {
        watcherThread.join();
    }
}

void Server::runLoop(JNIEnv* env, function<void(exception_ptr)> notifyStarted) {
    try {
        notifyStarted(nullptr);
    } catch (...) {
        notifyStarted(current_exception());
    }

    char* buffer[EVENT_BUFFER_SIZE];

    bool process = true;
    while (process) {
        ssize_t bytesRead = read(fdInotify, buffer, EVENT_BUFFER_SIZE);
        switch (bytesRead) {
            case -1:
                // TODO EINTR is the normal termination, right?
                log_severe(env, "Failed to fetch change notifications, errno = %d", errno);
                process = false;
                break;
            case 0:
                process = false;
                break;
            default:
                // Handle events
                break;
        }
    }
}

void Server::startWatching(const u16string& path) {
    if (watchPoints.find(path) != watchPoints.end()) {
        throw new FileWatcherException("Already watching path");
    }
    auto it = watchPoints.emplace(piecewise_construct,
        forward_as_tuple(path),
        forward_as_tuple(path, fdInotify));
    watchDescriptors.emplace(make_pair(it.first->second.watchDescriptor, path));
}

void Server::stopWatching(const u16string& path) {
    auto it = watchPoints.find(path);
    if (it == watchPoints.end()) {
        throw new FileWatcherException("Cannot stop watching path that was never watched");
    }
    watchDescriptors.erase(it->second.watchDescriptor);
    watchPoints.erase(it);
}

Server* getServer(JNIEnv* env, jobject javaServer) {
    Server* server = (Server*) env->GetDirectBufferAddress(javaServer);
    assert(server != NULL);
    return server;
}

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions_startWatcher(JNIEnv* env, jclass, jobject javaCallback) {
    Server* server;
    try {
        server = new Server(env, javaCallback);
    } catch (const exception& e) {
        log_severe(env, "Caught exception: %s", e.what());
        jclass exceptionClass = env->FindClass("net/rubygrapefruit/platform/NativeException");
        assert(exceptionClass != NULL);
        jint ret = env->ThrowNew(exceptionClass, e.what());
        assert(ret == 0);
        return NULL;
    }

    jclass clsWatcher = env->FindClass("net/rubygrapefruit/platform/internal/jni/LinuxFileEventFunctions$WatcherImpl");
    jmethodID constructor = env->GetMethodID(clsWatcher, "<init>", "(Ljava/lang/Object;)V");
    return env->NewObject(clsWatcher, constructor, env->NewDirectByteBuffer(server, sizeof(server)));
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions_00024WatcherImpl_startWatching(JNIEnv* env, jobject, jobject javaServer, jstring javaPath) {
    Server* server = getServer(env, javaServer);
    u16string path = javaToNativeString(env, javaPath);
    server->startWatching(path);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions_00024WatcherImpl_stopWatching(JNIEnv* env, jobject, jobject javaServer, jstring javaPath) {
    Server* server = getServer(env, javaServer);
    u16string path = javaToNativeString(env, javaPath);
    server->stopWatching(path);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions_00024WatcherImpl_stop(JNIEnv* env, jobject, jobject javaServer) {
    Server* server = getServer(env, javaServer);
    delete server;
}

#endif
