#ifndef __INCLUDE_GENERIC_H__
#define __INCLUDE_GENERIC_H__

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Marks the given result as failed, using the given error message
 */
extern void mark_failed_with_message(JNIEnv *env, const char* message, jobject result);

/*
 * Marks the given result as failed, using the given error message and the current value of errno/GetLastError()
 */
extern void mark_failed_with_errno(JNIEnv *env, const char* message, jobject result);

/*
 * Marks the given result as failed, using the given error message and error code
 */
extern void mark_failed_with_code(JNIEnv *env, const char* message, int error_code, jobject result);

#ifdef __cplusplus
}
#endif

#endif
