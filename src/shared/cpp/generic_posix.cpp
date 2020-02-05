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
 * POSIX platform functions.
 */
#ifndef _WIN32

#include "generic.h"
#include <errno.h>
#include <stdlib.h>
#include <string.h>

void mark_failed_with_errno(JNIEnv *env, const char* message, jobject result) {
    char* buffer = (char*)malloc(1024);
#if defined(__linux__) && _GNU_SOURCE
    // GNU semantics
    char* errno_message = strerror_r(errno, buffer, 1024);
#else
    strerror_r(errno, buffer, 1024);
    char* errno_message = buffer;
#endif
    mark_failed_with_code(env, message, errno, errno_message, result);
    free(buffer);
}

int map_error_code(int error_code) {
    if (error_code == ENOENT) {
        return FAILURE_NO_SUCH_FILE;
    }
    if (error_code == ENOTDIR) {
        return FAILURE_NOT_A_DIRECTORY;
    }
    if (error_code == EACCES) {
        return FAILURE_PERMISSIONS;
    }
    return FAILURE_GENERIC;
}

char* java_to_utf_char(JNIEnv *env, jstring string, jobject result) {
    size_t len = env->GetStringLength(string);
    size_t bytes = env->GetStringUTFLength(string);
    char* chars = (char*)malloc(bytes + 1);
    env->GetStringUTFRegion(string, 0, len, chars);
    chars[bytes] = 0;
    return chars;
}

jstring utf_char_to_java(JNIEnv* env, const char* chars, jobject result) {
    return env->NewStringUTF(chars);
}

int minimumLogLevel;
jclass clsLogger;
jmethodID logMethod;

JNIEXPORT void JNICALL Java_net_rubygrapefruit_platform_internal_jni_NativeLogger_initLogging(JNIEnv *env, jclass target, jint level) {
    minimumLogLevel = (int) level;
    clsLogger = env->FindClass("net/rubygrapefruit/platform/internal/jni/NativeLogger");
    logMethod = env->GetStaticMethodID(clsLogger, "log", "(ILjava/lang/String;)V");
    printlog(env, LOG_CONFIG, "Initialized logging to level %d\n", level);
}

void printlog(JNIEnv* env, int level, const char* fmt, ...) {
    if (minimumLogLevel > level) {
        return;
    }

    char buffer[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);

    if (env == NULL) {
        fprintf(stderr, "%s\n", buffer);
    } else {
        env->CallStaticVoidMethod(clsLogger, logMethod, level, env->NewStringUTF(buffer));
    }
}

#endif
