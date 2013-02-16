/*
 * Copyright 2012 Adam Murdoch
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

/*
 * Generic cross-platform functions.
 */
#include "native.h"
#include "generic.h"

void mark_failed_with_message(JNIEnv *env, const char* message, jobject result) {
    mark_failed_with_code(env, message, 0, NULL, result);
}

void mark_failed_with_code(JNIEnv *env, const char* message, int error_code, const char* error_code_message, jobject result) {
    jclass destClass = env->GetObjectClass(result);
    jmethodID method = env->GetMethodID(destClass, "failed", "(Ljava/lang/String;ILjava/lang/String;)V");
    jstring message_str = env->NewStringUTF(message);
    jstring error_code_str = error_code_message == NULL ? NULL : env->NewStringUTF(error_code_message);
    env->CallVoidMethod(result, method, message_str, error_code, error_code_str);
}

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_jni_NativeLibraryFunctions_getVersion(JNIEnv *env, jclass target) {
    return NATIVE_VERSION;
}
