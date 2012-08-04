/*
 * Generic functions
 */
#include "native.h"

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_NativeLibraryFunctions_getVersion(JNIEnv *env, jclass target) {
    return 2;
}
