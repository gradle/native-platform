#include "generic_fsnotifier.h"

AbstractServer::AbstractServer(JNIEnv* env) {
    JavaVM* jvm;
    int jvmStatus = env->GetJavaVM(&jvm);
    if (jvmStatus < 0) {
        throw FileWatcherException("Could not store jvm instance");
    }
    this->jvm = jvm;
}

static JNIEnv* lookupThreadEnv(JavaVM* jvm) {
    JNIEnv* env;
    // TODO Verify that JNI 1.6 is the right version
    jint ret = jvm->GetEnv((void**) &(env), JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        fprintf(stderr, "Failed to get JNI env for current thread: %d\n", ret);
        throw FileWatcherException("Failed to get JNI env for current thread");
    }
    return env;
}

JNIEnv* AbstractServer::getThreadEnv() {
    return lookupThreadEnv(jvm);
}
