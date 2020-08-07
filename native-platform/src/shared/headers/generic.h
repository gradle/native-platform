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

#ifndef __INCLUDE_GENERIC_H__
#define __INCLUDE_GENERIC_H__

#include <jni.h>

#include "native_platform_version.h"

#ifdef __cplusplus
extern "C" {
#endif

// Corresponds to NativeLibraryFunctions constants
#define STDOUT_DESCRIPTOR 0
#define STDERR_DESCRIPTOR 1
#define STDIN_DESCRIPTOR 2

// Corresponds to values of FileInfo.Type
#define FILE_TYPE_FILE 0
#define FILE_TYPE_DIRECTORY 1
#define FILE_TYPE_SYMLINK 2
#define FILE_TYPE_OTHER 3
#define FILE_TYPE_MISSING 4

// Corresponds to values of FunctionResult.Failure
#define FAILURE_GENERIC 0
#define FAILURE_NO_SUCH_FILE 1
#define FAILURE_NOT_A_DIRECTORY 2
#define FAILURE_PERMISSIONS 3

/*
 * Marks the given result as failed, using the given error message
 */
extern void mark_failed_with_message(JNIEnv* env, const char* message, jobject result);

/*
 * Marks the given result as failed, using the given error message and the current value of errno/GetLastError()
 */
extern void mark_failed_with_errno(JNIEnv* env, const char* message, jobject result);

/*
 * Marks the given result as failed, using the given error message and error code
 */
extern void mark_failed_with_code(JNIEnv* env, const char* message, int error_code, const char* error_code_message, jobject result);

/**
 * Maps system error code to a failure constant above.
 */
extern int map_error_code(int error_code);

/*
 * Converts the given Java string to a NULL terminated wchar_str. Should call free() when finished.
 *
 * Returns NULL on failure.
 */
extern wchar_t*
java_to_wchar(JNIEnv* env, jstring string, jobject result);

/*
 * Converts the given wchar_t string to a Java string.
 *
 * Returns NULL on failure.
 */
extern jstring wchar_to_java(JNIEnv* env, const wchar_t* chars, size_t len, jobject result);

/*
 * Converts the given Java string to a NULL terminated char string. Should call free() when finished.
 *
 * Returns NULL on failure.
 */
extern char* java_to_char(JNIEnv* env, jstring string, jobject result);

/*
 * Converts the given NULL terminated char string to a Java string.
 *
 * Returns NULL on failure.
 */
extern jstring char_to_java(JNIEnv* env, const char* chars, jobject result);

/*
 * Converts the given Java string to a NULL terminated char string (encoded with modified UTF-8). Should call free() when finished.
 *
 * Returns NULL on failure.
 */
extern char* java_to_utf_char(JNIEnv* env, jstring string, jobject result);

/*
 * Converts the given NULL terminated char string (encoded with modified UTF-8) to a Java string.
 *
 * Returns NULL on failure.
 */
extern jstring utf_char_to_java(JNIEnv* env, const char* chars, jobject result);

typedef struct file_stat {
    jint fileType;
    jlong lastModified;
    jlong size;
} file_stat_t;

#ifdef __cplusplus
}
#endif

#endif
