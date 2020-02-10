#ifdef __linux__

#include "linux_fsnotifier.h"

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions_startWatcher(JNIEnv* env, jclass, jobject javaCallback) {
    log_fine(env, "Hello, Linux", NULL);

    (void) javaCallback;

    jclass clsWatcher = env->FindClass("net/rubygrapefruit/platform/internal/jni/LinuxFileEventFunctions$WatcherImpl");
    jmethodID constructor = env->GetMethodID(clsWatcher, "<init>", "(Ljava/lang/Object;)V");
    return env->NewObject(clsWatcher, constructor, NULL);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions_00024WatcherImpl_startWatching(JNIEnv* env, jobject, jobject javaServer, jstring javaPath) {
    log_fine(env, "Start watching", NULL);
    (void) javaServer;
    (void) javaPath;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions_00024WatcherImpl_stopWatching(JNIEnv* env, jobject, jobject javaServer, jstring javaPath) {
    log_fine(env, "Stop watching", NULL);
    (void) javaServer;
    (void) javaPath;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions_00024WatcherImpl_stop(JNIEnv* env, jobject, jobject javaServer) {
    log_fine(env, "Good bye, Linux", NULL);
    (void) javaServer;
}

#endif
