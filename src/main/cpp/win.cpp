#ifdef WIN32

#include "native.h"
#include "generic.h"
#include <windows.h>
#include <wchar.h>

/*
 * Marks the given result as failed, using the current value of GetLastError()
 */
void mark_failed_with_errno(JNIEnv *env, const char* message, jobject result) {
    mark_failed_with_code(env, message, GetLastError(), result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_NativeLibraryFunctions_getSystemInfo(JNIEnv *env, jclass target, jobject info, jobject result) {
    jclass infoClass = env->GetObjectClass(info);

    OSVERSIONINFOEX versionInfo;
    versionInfo.dwOSVersionInfoSize = sizeof(OSVERSIONINFOEX);
    if (GetVersionEx((OSVERSIONINFO*)&versionInfo) == 0) {
        mark_failed_with_errno(env, "could not get version info", result);
        return;
    }

    SYSTEM_INFO systemInfo;
    GetNativeSystemInfo(&systemInfo);
    jstring arch = NULL;
    if (systemInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64) {
        arch = env->NewStringUTF("amd64");
    } else if (systemInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_INTEL) {
        arch = env->NewStringUTF("x86");
    } else if (systemInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_IA64) {
        arch = env->NewStringUTF("ia64");
    } else {
        arch = env->NewStringUTF("unknown");
    }

    jmethodID method = env->GetMethodID(infoClass, "windows", "(IIIZLjava/lang/String;)V");
    env->CallVoidMethod(info, method, versionInfo.dwMajorVersion, versionInfo.dwMinorVersion,
                        versionInfo.dwBuildNumber, versionInfo.wProductType == VER_NT_WORKSTATION,
                        arch);
}

/*
 * Process functions
 */

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getPid(JNIEnv *env, jclass target) {
    return GetCurrentProcessId();
}

/*
 * File system functions
 */
JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileSystemFunctions_listFileSystems(JNIEnv *env, jclass target, jobject info, jobject result) {
    wchar_t* volumeName = (wchar_t*)malloc(sizeof(wchar_t) * (MAX_PATH+1));

    jclass info_class = env->GetObjectClass(info);
    jmethodID method = env->GetMethodID(info_class, "add", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V");

    HANDLE handle = FindFirstVolumeW(volumeName, MAX_PATH+1);
    if (handle == INVALID_HANDLE_VALUE) {
        free(volumeName);
        mark_failed_with_errno(env, "could not find first volume", result);
        return;
    }

    wchar_t* deviceName = (wchar_t*)malloc(sizeof(wchar_t) * (MAX_PATH+1));
    wchar_t* pathNames = (wchar_t*)malloc(sizeof(wchar_t) * (MAX_PATH+1));
    wchar_t* fsName = (wchar_t*)malloc(sizeof(wchar_t) * (MAX_PATH+1));

    while(true) {
        // Chop off the trailing '\'
        size_t len = wcslen(volumeName);
        if (len < 5) {
            mark_failed_with_message(env, "volume name is too short", result);
            break;
        }
        volumeName[len-1] = L'\0';

        if (QueryDosDeviceW(&volumeName[4], deviceName, MAX_PATH+1) == 0) {
            mark_failed_with_errno(env, "could not query dos device", result);
            break;
        }
        volumeName[len-1] = L'\\';

        DWORD used;
        if (GetVolumePathNamesForVolumeNameW(volumeName, pathNames, MAX_PATH+1, &used) == 0) {
            // TODO - try again if the buffer is too small
            mark_failed_with_errno(env, "could not query volume paths", result);
            break;
        }

        wchar_t* cur = pathNames;
        if (cur[0] != L'\0') {
            if(GetVolumeInformationW(cur, NULL, 0, NULL, NULL, NULL, fsName, MAX_PATH+1) == 0) {
                if (GetLastError() != ERROR_NOT_READY) {
                    mark_failed_with_errno(env, "could not query volume information", result);
                    break;
                }
                wcscpy(fsName, L"unknown");
            }
            for (;cur[0] != L'\0'; cur += wcslen(cur) + 1) {
                env->CallVoidMethod(info, method, env->NewString((jchar*)deviceName, wcslen(deviceName)),
                                    env->NewString((jchar*)fsName, wcslen(fsName)), env->NewString((jchar*)cur, wcslen(cur)), JNI_FALSE);
            }
        }

        if (FindNextVolumeW(handle, volumeName, MAX_PATH) == 0) {
            if (GetLastError() != ERROR_NO_MORE_FILES) {
                mark_failed_with_errno(env, "could find next volume", result);
            }
            break;
        }
    }
    free(volumeName);
    free(deviceName);
    free(pathNames);
    free(fsName);
    FindVolumeClose(handle);
}

/*
 * Console functions
 */

HANDLE getHandle(JNIEnv *env, int output, jobject result) {
    HANDLE handle = output == 0 ? GetStdHandle(STD_OUTPUT_HANDLE) : GetStdHandle(STD_ERROR_HANDLE);
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
    current_attributes = original_attributes;
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
    DWORD count;
    if (!FillConsoleOutputCharacterW(current_console, L' ', console_info.dwSize.X - console_info.dwCursorPosition.X, console_info.dwCursorPosition, &count)) {
        mark_failed_with_errno(env, "could not clear console", result);
    }
}

#endif
