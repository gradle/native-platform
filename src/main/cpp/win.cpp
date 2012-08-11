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

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_NativeLibraryFunctions_getPlatform(JNIEnv *env, jclass target) {
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
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_getConsoleSize(JNIEnv *env, jclass target, jint output, jobject dimension, jobject result) {
    CONSOLE_SCREEN_BUFFER_INFO console_info;
    HANDLE handle = getHandle(env, output, result);
    if (handle == NULL) {
        mark_failed_with_message(env, "not a console", result);
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

HANDLE current_console = NULL;
WORD original_attributes = 0;
WORD current_attributes = 0;

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_initConsole(JNIEnv *env, jclass target, jint output, jobject result) {
    CONSOLE_SCREEN_BUFFER_INFO console_info;
    HANDLE handle = getHandle(env, output, result);
    if (handle == NULL) {
        mark_failed_with_message(env, "not a terminal", result);
        return;
    }
    if (!GetConsoleScreenBufferInfo(handle, &console_info)) {
        if (GetLastError() == ERROR_INVALID_HANDLE) {
            mark_failed_with_message(env, "not a console", result);
        } else {
            mark_failed_with_errno(env, "could not get console buffer", result);
        }
        return;
    }
    current_console = handle;
    original_attributes = console_info.wAttributes;
    current_attributes = original_attributes;
    Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_normal(env, target, result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_bold(JNIEnv *env, jclass target, jobject result) {
    current_attributes |= FOREGROUND_INTENSITY;
    if (!SetConsoleTextAttribute(current_console, current_attributes)) {
        mark_failed_with_errno(env, "could not set text attributes", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_normal(JNIEnv *env, jclass target, jobject result) {
    current_attributes &= ~FOREGROUND_INTENSITY;
    SetConsoleTextAttribute(current_console, current_attributes);
    if (!SetConsoleTextAttribute(current_console, current_attributes)) {
        mark_failed_with_errno(env, "could not set text attributes", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_reset(JNIEnv *env, jclass target, jobject result) {
    SetConsoleTextAttribute(current_console, original_attributes);
    if (!SetConsoleTextAttribute(current_console, current_attributes)) {
        mark_failed_with_errno(env, "could not set text attributes", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_foreground(JNIEnv *env, jclass target, jint color, jobject result) {
    current_attributes &= ~ (FOREGROUND_BLUE|FOREGROUND_RED|FOREGROUND_GREEN);
    switch (color) {
        case 0:
            break;
        case 1:
            current_attributes |= FOREGROUND_RED;
            break;
        case 2:
            current_attributes |= FOREGROUND_GREEN;
            break;
        case 3:
            current_attributes |= FOREGROUND_RED|FOREGROUND_GREEN;
            break;
        case 4:
            current_attributes |= FOREGROUND_BLUE;
            break;
        case 5:
            current_attributes |= FOREGROUND_RED|FOREGROUND_BLUE;
            break;
        case 6:
            current_attributes |= FOREGROUND_GREEN|FOREGROUND_BLUE;
            break;
        default:
            current_attributes |= FOREGROUND_RED|FOREGROUND_GREEN|FOREGROUND_BLUE;
            break;
    }

    SetConsoleTextAttribute(current_console, current_attributes);
    if (!SetConsoleTextAttribute(current_console, current_attributes)) {
        mark_failed_with_errno(env, "could not set text attributes", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_left(JNIEnv *env, jclass target, jint count, jobject result) {
    CONSOLE_SCREEN_BUFFER_INFO console_info;
    if (!GetConsoleScreenBufferInfo(current_console, &console_info)) {
        mark_failed_with_errno(env, "could not get console buffer", result);
        return;
    }
    console_info.dwCursorPosition.X -= count;
    if (!SetConsoleCursorPosition(current_console, console_info.dwCursorPosition)) {
        mark_failed_with_errno(env, "could not set cursor position", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_right(JNIEnv *env, jclass target, jint count, jobject result) {
    CONSOLE_SCREEN_BUFFER_INFO console_info;
    if (!GetConsoleScreenBufferInfo(current_console, &console_info)) {
        mark_failed_with_errno(env, "could not get console buffer", result);
        return;
    }
    console_info.dwCursorPosition.X += count;
    if (!SetConsoleCursorPosition(current_console, console_info.dwCursorPosition)) {
        mark_failed_with_errno(env, "could not set cursor position", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_up(JNIEnv *env, jclass target, jint count, jobject result) {
    CONSOLE_SCREEN_BUFFER_INFO console_info;
    if (!GetConsoleScreenBufferInfo(current_console, &console_info)) {
        mark_failed_with_errno(env, "could not get console buffer", result);
        return;
    }
    console_info.dwCursorPosition.Y -= count;
    if (!SetConsoleCursorPosition(current_console, console_info.dwCursorPosition)) {
        mark_failed_with_errno(env, "could not set cursor position", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_down(JNIEnv *env, jclass target, jint count, jobject result) {
    CONSOLE_SCREEN_BUFFER_INFO console_info;
    if (!GetConsoleScreenBufferInfo(current_console, &console_info)) {
        mark_failed_with_errno(env, "could not get console buffer", result);
        return;
    }
    console_info.dwCursorPosition.Y += count;
    if (!SetConsoleCursorPosition(current_console, console_info.dwCursorPosition)) {
        mark_failed_with_errno(env, "could not set cursor position", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_startLine(JNIEnv *env, jclass target, jobject result) {
    CONSOLE_SCREEN_BUFFER_INFO console_info;
    if (!GetConsoleScreenBufferInfo(current_console, &console_info)) {
        mark_failed_with_errno(env, "could not get console buffer", result);
        return;
    }
    console_info.dwCursorPosition.X = 0;
    if (!SetConsoleCursorPosition(current_console, console_info.dwCursorPosition)) {
        mark_failed_with_errno(env, "could not set cursor position", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_clearToEndOfLine(JNIEnv *env, jclass target, jobject result) {
    CONSOLE_SCREEN_BUFFER_INFO console_info;
    if (!GetConsoleScreenBufferInfo(current_console, &console_info)) {
        mark_failed_with_errno(env, "could not get console buffer", result);
        return;
    }
    for (int i = console_info.dwCursorPosition.X; i < console_info.dwSize.X; i++) {
        WriteConsole(current_console, " ", 1, NULL, NULL);
    }
}

#endif
