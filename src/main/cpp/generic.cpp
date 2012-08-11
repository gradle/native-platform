/*
 * Generic functions
 */
#include "native.h"
#include "generic.h"

void mark_failed_with_message(JNIEnv *env, const char* message, jobject result) {
    mark_failed_with_code(env, message, 0, result);
}

void mark_failed_with_code(JNIEnv *env, const char* message, int error_code, jobject result) {
    jclass destClass = env->GetObjectClass(result);
    jmethodID method = env->GetMethodID(destClass, "failed", "(Ljava/lang/String;I)V");
    jstring message_str = env->NewStringUTF(message);
    env->CallVoidMethod(result, method, message_str, error_code);
}

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_jni_NativeLibraryFunctions_getVersion(JNIEnv *env, jclass target) {
    return 5;
}
