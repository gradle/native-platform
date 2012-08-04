#ifdef WIN32

#include "native.h"
#include "generic.h"
#include <windows.h>

/*
 * Marks the given result as failed, using the current value of GetLastError()
 */
void mark_failed_with_errno(JNIEnv *env, const char* message, jobject result) {
    mark_failed_with_code(env, message, GetLastError(), result);
}

/*
 * Process functions
 */

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getPid(JNIEnv *env, jclass target) {
    return GetCurrentProcessId();
}

/*
 * Console functions
 */

HANDLE getHandle(JNIEnv *env, int output, jobject result) {
    HANDLE handle = output == 1 ? GetStdHandle(STD_OUTPUT_HANDLE) : GetStdHandle(STD_ERROR_HANDLE);
    if (handle == INVALID_HANDLE_VALUE) {
        mark_failed_with_errno(env, "could not get console handle", result);
        return NULL;
    }
    return handle;
}

JNIEXPORT jboolean JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_isConsole(JNIEnv *env, jclass target, jint output, jobject result) {
    CONSOLE_SCREEN_BUFFER_INFO console_info;
    HANDLE handle = getHandle(env, output, result);
    if (handle == NULL) {
        return JNI_FALSE;
    }
    if (!GetConsoleScreenBufferInfo(handle, &console_info)) {
        if (GetLastError() == ERROR_INVALID_HANDLE) {
            return JNI_FALSE;
        }
        mark_failed_with_errno(env, "could not get console buffer", result);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_getConsoleSize (JNIEnv *env, jclass target, jint output, jobject dimension, jobject result) {
    CONSOLE_SCREEN_BUFFER_INFO console_info;
    HANDLE handle = getHandle(env, output, result);
    if (handle == NULL) {
        mark_failed_with_message(env, "not a terminal", result);
        return;
    }
    if (!GetConsoleScreenBufferInfo(handle, &console_info)) {
        mark_failed_with_errno(env, "could not get console buffer", result);
        return;
    }

    jclass dimensionClass = env->GetObjectClass(dimension);
    jfieldID widthField = env->GetFieldID(dimensionClass, "cols", "I");
    env->SetIntField(dimension, widthField, console_info.srWindow.Right - console_info.srWindow.Left + 1);
    jfieldID heightField = env->GetFieldID(dimensionClass, "rows", "I");
    env->SetIntField(dimension, heightField, console_info.srWindow.Bottom - console_info.srWindow.Top + 1);
}

#endif
