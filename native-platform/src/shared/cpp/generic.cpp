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
#include "generic.h"
#include "net_rubygrapefruit_platform_internal_jni_NativeLibraryFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_StringEncodingHelper.h"
#include <stdlib.h>
#include <string.h>

void mark_failed_with_message(JNIEnv* env, const char* message, jobject result) {
    mark_failed_with_code(env, message, 0, NULL, result);
}

void mark_failed_with_code(JNIEnv* env, const char* message, int error_code, const char* error_code_message, jobject result) {
    jclass destClass = env->GetObjectClass(result);
    jmethodID method = env->GetMethodID(destClass, "failed", "(Ljava/lang/String;IILjava/lang/String;)V");
    jstring message_str = env->NewStringUTF(message);
    jstring error_code_str = error_code_message == NULL ? NULL : env->NewStringUTF(error_code_message);
    jint failure_code = map_error_code(error_code);
    env->CallVoidMethod(result, method, message_str, failure_code, error_code, error_code_str);
    if (error_code_str != NULL) {
        env->DeleteLocalRef(error_code_str);
    }
}

static jclass string_encoding_helper_class = nullptr;
static jmethodID string_encoding_helper_to_utf8_method_id = nullptr;
static jmethodID string_encoding_helper_from_utf8_method_id = nullptr;

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_StringEncodingHelper_initialize(JNIEnv* env, jclass target) {
    string_encoding_helper_class = (jclass) env->NewGlobalRef(target);
    string_encoding_helper_to_utf8_method_id = env->GetStaticMethodID(string_encoding_helper_class, "toUtf8", "(Ljava/lang/String;)[B");
    if (env->ExceptionCheck()) {
        return;
    }
    string_encoding_helper_from_utf8_method_id = env->GetStaticMethodID(string_encoding_helper_class, "fromUtf8", "([B)Ljava/lang/String;");
    // ExceptionCheck elided because this is the last statement.
}

// Validate that a char and a jbyte have the same size.
static_assert(sizeof(char) == sizeof(jbyte), "char and jbyte must be the same size");

// result is unused now, but may be used in the future if we stop calling into Java methods.
char* jstring_to_str(JNIEnv* env, jstring string, jobject result) {
    jbyteArray utf8_bytes = (jbyteArray) env->CallStaticObjectMethod(
        string_encoding_helper_class,
        string_encoding_helper_to_utf8_method_id,
        string
    );
    if (env->ExceptionCheck()) {
        return NULL;
    }
    jsize length = env->GetArrayLength(utf8_bytes);
    char* buffer = (char*) malloc(length + 1);
    env->GetByteArrayRegion(utf8_bytes, 0, length, (jbyte*) buffer);
    if (env->ExceptionCheck()) {
        free(buffer);
        return NULL;
    }
    buffer[length] = '\0';
    env->DeleteLocalRef(utf8_bytes);
    return buffer;
}

jstring str_to_jstring(JNIEnv* env, const char* str, jobject result) {
    size_t length = strlen(str);
    if (length > INT32_MAX) {
        mark_failed_with_message(env, "String too long to convert to jstring", result);
        return NULL;
    }
    jsize jlength = (jsize) length;
    jbyteArray utf8_bytes = env->NewByteArray(jlength);
    if (env->ExceptionCheck()) {
        return NULL;
    }
    env->SetByteArrayRegion(utf8_bytes, 0, jlength, (const jbyte*) str);
    if (env->ExceptionCheck()) {
        return NULL;
    }
    jstring result_string = (jstring) env->CallStaticObjectMethod(
        string_encoding_helper_class,
        string_encoding_helper_from_utf8_method_id,
        utf8_bytes
    );
    if (env->ExceptionCheck()) {
        return NULL;
    }
    env->DeleteLocalRef(utf8_bytes);
    return result_string;
}

JNIEXPORT jstring JNICALL
Java_net_rubygrapefruit_platform_internal_jni_NativeLibraryFunctions_getVersion(JNIEnv* env, jclass target) {
    return env->NewStringUTF(NATIVE_VERSION);
}
