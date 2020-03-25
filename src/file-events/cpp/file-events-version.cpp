#include "native_platform_version.h"
#include "net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions.h"

JNIEXPORT jstring JNICALL
Java_net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions_getVersion0(JNIEnv* env, jclass) {
    return env->NewStringUTF(NATIVE_VERSION);
}
