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

#ifdef WIN32

#include "native.h"
#include "generic.h"
#include <windows.h>
#include <wchar.h>

/*
 * Marks the given result as failed, using the current value of GetLastError()
 */
void mark_failed_with_errno(JNIEnv *env, const char* message, jobject result) {
    mark_failed_with_code(env, message, GetLastError(), NULL, result);
}

jstring wchar_to_java(JNIEnv* env, const wchar_t* chars, size_t len, jobject result) {
    if (sizeof(wchar_t) != 2) {
        mark_failed_with_message(env, "unexpected size of wchar_t", result);
        return NULL;
    }
    return env->NewString((jchar*)chars, len);
}

wchar_t* java_to_wchar(JNIEnv *env, jstring string, jobject result) {
    jsize len = env->GetStringLength(string);
    wchar_t* str = (wchar_t*)malloc(sizeof(wchar_t) * (len+1));
    env->GetStringRegion(string, 0, len, (jchar*)str);
    str[len] = L'\0';
    return str;
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

JNIEXPORT jstring JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getWorkingDirectory(JNIEnv *env, jclass target, jobject result) {
    DWORD size = GetCurrentDirectoryW(0, NULL);
    if (size == 0) {
        mark_failed_with_errno(env, "could not determine length of working directory path", result);
        return NULL;
    }
    size = size+1; // Needs to include null character
    wchar_t* path = (wchar_t*)malloc(sizeof(wchar_t) * size);
    DWORD copied = GetCurrentDirectoryW(size, path);
    if (copied == 0) {
        free(path);
        mark_failed_with_errno(env, "could get working directory path", result);
        return NULL;
    }
    jstring dirName = wchar_to_java(env, path, copied, result);
    free(path);
    return dirName;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_setWorkingDirectory(JNIEnv *env, jclass target, jstring dir, jobject result) {
    wchar_t* dirPath = java_to_wchar(env, dir, result);
    if (dirPath == NULL) {
        return;
    }
    BOOL ok = SetCurrentDirectoryW(dirPath);
    free(dirPath);
    if (!ok) {
        mark_failed_with_errno(env, "could not set current directory", result);
        return;
    }
}

JNIEXPORT jstring JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getEnvironmentVariable(JNIEnv *env, jclass target, jstring var, jobject result) {
    wchar_t* varStr = java_to_wchar(env, var, result);
    DWORD len = GetEnvironmentVariableW(varStr, NULL, 0);
    if (len == 0) {
        if (GetLastError() != ERROR_ENVVAR_NOT_FOUND) {
            mark_failed_with_errno(env, "could not determine length of environment variable", result);
        }
        free(varStr);
        return NULL;
    }
    wchar_t* valueStr = (wchar_t*)malloc(sizeof(wchar_t) * len);
    DWORD copied = GetEnvironmentVariableW(varStr, valueStr, len);
    if (copied == 0) {
        if (len > 1) {
            // If the value is empty, then copied will be 0
            mark_failed_with_errno(env, "could not get environment variable", result);
        }
        free(varStr);
        free(valueStr);
        return NULL;
    }
    free(varStr);
    jstring value = wchar_to_java(env, valueStr, copied, result);
    free(valueStr);
    return value;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_setEnvironmentVariable(JNIEnv *env, jclass target, jstring var, jstring value, jobject result) {
    wchar_t* varStr = java_to_wchar(env, var, result);
    wchar_t* valueStr = value == NULL ? NULL : java_to_wchar(env, value, result);
    BOOL ok = SetEnvironmentVariableW(varStr, valueStr);
    free(varStr);
    if (valueStr != NULL) {
        free(valueStr);
    }
    if (!ok && GetLastError() != ERROR_ENVVAR_NOT_FOUND) {
        mark_failed_with_errno(env, "could not set environment var", result);
        return;
    }
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
            // TODO - use GetDriveTypeW() to determine if removable, remote, etc
            if(GetVolumeInformationW(cur, NULL, 0, NULL, NULL, NULL, fsName, MAX_PATH+1) == 0) {
                if (GetLastError() != ERROR_NOT_READY) {
                    mark_failed_with_errno(env, "could not query volume information", result);
                    break;
                }
                wcscpy(fsName, L"unknown");
            }
            for (;cur[0] != L'\0'; cur += wcslen(cur) + 1) {
                env->CallVoidMethod(info, method,
                                    wchar_to_java(env, cur, wcslen(cur), result),
                                    wchar_to_java(env, fsName, wcslen(fsName), result),
                                    wchar_to_java(env, deviceName, wcslen(deviceName), result),
                                    JNI_FALSE);
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

void uninheritStream(JNIEnv *env, DWORD stdInputHandle, jobject result) {
    HANDLE streamHandle = GetStdHandle(stdInputHandle);
    if (streamHandle == NULL) {
        // We're not attached to a stdio (eg Desktop application). Ignore.
        return;
    }
    if (streamHandle == INVALID_HANDLE_VALUE) {
        mark_failed_with_errno(env, "could not get std handle", result);
        return;
    }
    boolean ok = SetHandleInformation(streamHandle, HANDLE_FLAG_INHERIT, 0);
    if (!ok) {
        mark_failed_with_errno(env, "could not change std handle", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsHandleFunctions_markStandardHandlesUninheritable(JNIEnv *env, jclass target, jobject result) {
    uninheritStream(env, STD_INPUT_HANDLE, result);
    uninheritStream(env, STD_OUTPUT_HANDLE, result);
    uninheritStream(env, STD_ERROR_HANDLE, result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsHandleFunctions_restoreStandardHandles(JNIEnv *env, jclass target, jobject result) {
}

#endif
