#if defined(_WIN32) || defined(__APPLE__)

#include "generic_fsnotifier.h"

/**
 * Attaches JNI to the current thread.
 */
extern JNIEnv* attach_jni(JavaVM* jvm, const char* name, bool daemon);

/**
 * Detaches JNI from the current thread.
 */
extern int detach_jni(JavaVM* jvm);

AbstractServer::AbstractServer(JNIEnv* env, jobject watcherCallback) {
    JavaVM* jvm;
    int jvmStatus = env->GetJavaVM(&jvm);
    if (jvmStatus < 0) {
        throw FileWatcherException("Could not store jvm instance");
    }
    this->jvm = jvm;

    jclass callbackClass = env->GetObjectClass(watcherCallback);
    this->watcherCallbackMethod = env->GetMethodID(callbackClass, "pathChanged", "(ILjava/lang/String;)V");

    jobject globalWatcherCallback = env->NewGlobalRef(watcherCallback);
    if (globalWatcherCallback == NULL) {
        throw FileWatcherException("Could not get global ref for watcher callback");
    }
    this->watcherCallback = globalWatcherCallback;
}

AbstractServer::~AbstractServer() {
    JNIEnv* env = getThreadEnv();
    if (env != NULL) {
        env->DeleteGlobalRef(watcherCallback);
    }
}

void AbstractServer::startThread() {
    unique_lock<mutex> lock(watcherThreadMutex);
    this->watcherThread = thread(&AbstractServer::run, this);
    this->watcherThreadStarted.wait(lock);
}

void AbstractServer::run() {
    JNIEnv* env = attach_jni(jvm, "File watcher server", true);

    runLoop(env, [this] {
        unique_lock<mutex> lock(watcherThreadMutex);
        watcherThreadStarted.notify_all();
    });

    detach_jni(jvm);
}

JNIEnv* AbstractServer::getThreadEnv() {
    JNIEnv* env;
    jint ret = jvm->GetEnv((void**) &(env), JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        fprintf(stderr, "Failed to get JNI env for current thread: %d\n", ret);
        throw FileWatcherException("Failed to get JNI env for current thread");
    }
    return env;
}

void AbstractServer::reportChange(JNIEnv* env, int type, const u16string& path) {
    jstring javaPath = env->NewString((jchar*) path.c_str(), path.length());
    env->CallVoidMethod(watcherCallback, watcherCallbackMethod, type, javaPath);
    env->DeleteLocalRef(javaPath);
}

JNIEnv* attach_jni(JavaVM* jvm, const char* name, bool daemon) {
    JNIEnv* env;
    // Work around const char* issue
    char* nameCopy = strdup(name);
    JavaVMAttachArgs args = {
        JNI_VERSION_1_6,    // version
        nameCopy,           // thread name
        NULL                // thread group
    };
    free(nameCopy);
    jint ret = daemon
        ? jvm->AttachCurrentThreadAsDaemon((void**) &(env), (void*) &args)
        : jvm->AttachCurrentThread((void**) &(env), (void*) &args);
    if (ret != JNI_OK) {
        fprintf(stderr, "Failed to attach JNI to current thread: %d\n", ret);
        return NULL;
    }
    return env;
}

int detach_jni(JavaVM* jvm) {
    jint ret = jvm->DetachCurrentThread();
    if (ret != JNI_OK) {
        fprintf(stderr, "Failed to detach JNI from current thread: %d\n", ret);
    }
    return ret;
}

#endif
