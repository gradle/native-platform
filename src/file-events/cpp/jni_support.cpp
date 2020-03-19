#include <exception>
#include <string>

#include "jni_support.h"

using namespace std;

JavaVM* getJavaVm(JNIEnv* env) {
    JavaVM* jvm;
    int jvmStatus = env->GetJavaVM(&jvm);
    if (jvmStatus != 0) {
        throw runtime_error(string("Could not get jvm instance: ") + to_string(jvmStatus));
    }
    return jvm;
}

JniSupport::JniSupport(JavaVM* jvm)
    : jvm(jvm) {
}

JniSupport::JniSupport(JNIEnv* env)
    : jvm(getJavaVm(env)) {
}

JNIEnv* JniSupport::getThreadEnv() {
    JNIEnv* env;
    jint ret = jvm->GetEnv((void**) &env, JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        throw runtime_error(string("Failed to get JNI env for current thread: ") + to_string(ret));
    }
    return env;
}

JniThreadAttacher::JniThreadAttacher(JavaVM* jvm, const char* name, bool daemon)
    : JniSupport(jvm) {
    JNIEnv* env;
    JavaVMAttachArgs args = {
        JNI_VERSION_1_6,            // version
        const_cast<char*>(name),    // thread name
        NULL                        // thread group
    };
    jint ret = daemon
        ? jvm->AttachCurrentThreadAsDaemon((void**) &env, (void*) &args)
        : jvm->AttachCurrentThread((void**) &env, (void*) &args);
    if (ret != JNI_OK) {
        fprintf(stderr, "Failed to attach JNI to current thread: %d\n", ret);
        throw runtime_error(string("Failed to attach JNI to current thread: ") + to_string(ret));
    }
}

JniThreadAttacher::~JniThreadAttacher() {
    jint ret = jvm->DetachCurrentThread();
    if (ret != JNI_OK) {
        fprintf(stderr, "Failed to detach JNI from current thread: %d\n", ret);
    }
}
