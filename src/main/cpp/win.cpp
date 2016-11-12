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

#ifdef _WIN32

#include "native.h"
#include "generic.h"
#include <windows.h>
#include <Shlwapi.h>
#include <wchar.h>

/*
 * Marks the given result as failed, using the current value of GetLastError()
 */
void mark_failed_with_errno(JNIEnv *env, const char* message, jobject result) {
    mark_failed_with_code(env, message, GetLastError(), NULL, result);
}

int map_error_code(int error_code) {
    if (error_code == ERROR_PATH_NOT_FOUND) {
        return FAILURE_NO_SUCH_FILE;
    }
    if (error_code == ERROR_DIRECTORY) {
        return FAILURE_NOT_A_DIRECTORY;
    }
    return FAILURE_GENERIC;
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

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_detach(JNIEnv *env, jclass target, jobject result) {
    if (FreeConsole() == 0) {
        // Ignore if the error is that the process is already detached from the console
        if (GetLastError() != ERROR_INVALID_PARAMETER) {
            mark_failed_with_errno(env, "could not FreeConsole()", result);
        }
    }
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
    jclass info_class = env->GetObjectClass(info);
    jmethodID method = env->GetMethodID(info_class, "add", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZZ)V");

    DWORD required = GetLogicalDriveStringsW(0, NULL);
    if (required == 0) {
        mark_failed_with_errno(env, "could not determine logical drive buffer size", result);
        return;
    }

    wchar_t* buffer = (wchar_t*)malloc(sizeof(wchar_t) * (required + 1));
    wchar_t* deviceName = (wchar_t*)malloc(sizeof(wchar_t) * (MAX_PATH + 1));
    wchar_t* fileSystemName = (wchar_t*)malloc(sizeof(wchar_t) * (MAX_PATH + 1));

    if (GetLogicalDriveStringsW(required, buffer) == 0) {
        mark_failed_with_errno(env, "could not determine logical drives", result);
    } else {
        wchar_t* cur = buffer;
        for (;cur[0] != L'\0'; cur += wcslen(cur) + 1) {
            DWORD type = GetDriveTypeW(cur);
            jboolean remote = type == DRIVE_REMOTE;

            // chop off trailing '\'
            size_t len = wcslen(cur);
            cur[len-1] = L'\0';

            // create device name \\.\C:
            wchar_t devPath[7];
            swprintf(devPath, 7, L"\\\\.\\%s", cur);

            if (QueryDosDeviceW(cur, deviceName, MAX_PATH+1) == 0) {
                mark_failed_with_errno(env, "could not map device for logical drive", result);
                break;
            }
            cur[len-1] = L'\\';

            DWORD available = 1;
            if (!remote) {
                HANDLE hDevice = CreateFileW(devPath,         // like "\\.\E:"
                                             FILE_READ_ATTRIBUTES, // read access to the attributes
                                             FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE, // share mode
                                             NULL, OPEN_EXISTING, 0, NULL);
                if (hDevice != INVALID_HANDLE_VALUE) {
                    DWORD cbBytesReturned;
                    DWORD bSuccess = DeviceIoControl (hDevice,                     // device to be queried
                                                IOCTL_STORAGE_CHECK_VERIFY2,
                                                NULL, 0,                     // no input buffer
                                                NULL, 0,                     // no output buffer
                                                &cbBytesReturned,            // # bytes returned
                                                (LPOVERLAPPED) NULL);        // synchronous I/O
                    if (!bSuccess) {
                        available = 0;
                    }
                    CloseHandle(hDevice);
                }
            }

            jboolean casePreserving = JNI_TRUE;
            if (available) {
                DWORD flags;
                if (GetVolumeInformationW(cur, NULL, 0, NULL, NULL, &flags, fileSystemName, MAX_PATH+1) == 0) {
                    mark_failed_with_errno(env, "could not get volume information", result);
                    break;
                }
                casePreserving = (flags & FILE_CASE_PRESERVED_NAMES) != 0;
            } else {
                if (type == DRIVE_CDROM) {
                    swprintf(fileSystemName, MAX_PATH+1, L"cdrom");
                } else {
                    swprintf(fileSystemName, MAX_PATH+1, L"unknown");
                }
            }

            env->CallVoidMethod(info, method,
                                wchar_to_java(env, cur, wcslen(cur), result),
                                wchar_to_java(env, fileSystemName, wcslen(fileSystemName), result),
                                wchar_to_java(env, deviceName, wcslen(deviceName), result),
                                remote, JNI_FALSE, casePreserving);
        }
    }

    free(buffer);
    free(deviceName);
    free(fileSystemName);
}

typedef struct watch_details {
    HANDLE watch_handle;
} watch_details_t;

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_FileEventFunctions_createWatch(JNIEnv *env, jclass target, jstring path, jobject result) {
    wchar_t* pathStr = java_to_wchar(env, path, result);
    HANDLE h = FindFirstChangeNotificationW(pathStr, FALSE, FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME | FILE_NOTIFY_CHANGE_ATTRIBUTES | FILE_NOTIFY_CHANGE_SIZE | FILE_NOTIFY_CHANGE_LAST_WRITE);
    free(pathStr);
    if (h == INVALID_HANDLE_VALUE) {
        mark_failed_with_errno(env, "could not open change notification", result);
        return NULL;
    }
    watch_details_t* details = (watch_details_t*)malloc(sizeof(watch_details_t));
    details->watch_handle = h;
    return env->NewDirectByteBuffer(details, sizeof(watch_details_t));
}

JNIEXPORT jboolean JNICALL
Java_net_rubygrapefruit_platform_internal_jni_FileEventFunctions_waitForNextEvent(JNIEnv *env, jclass target, jobject handle, jobject result) {
    watch_details_t* details = (watch_details_t*)env->GetDirectBufferAddress(handle);
    if (WaitForSingleObject(details->watch_handle, INFINITE) == WAIT_FAILED) {
        mark_failed_with_errno(env, "could not wait for change notification", result);
        return JNI_FALSE;
    }
    if (!FindNextChangeNotification(details->watch_handle)) {
        if (GetLastError() == ERROR_INVALID_HANDLE) {
            // Assumed closed
            return JNI_FALSE;
        }
        mark_failed_with_errno(env, "could not schedule next change notification", result);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_FileEventFunctions_closeWatch(JNIEnv *env, jclass target, jobject handle, jobject result) {
    watch_details_t* details = (watch_details_t*)env->GetDirectBufferAddress(handle);
    FindCloseChangeNotification(details->watch_handle);
    free(details);
}

jlong lastModifiedNanos(FILETIME* time) {
    return ((jlong)time->dwHighDateTime << 32) | time->dwLowDateTime;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsFileFunctions_stat(JNIEnv *env, jclass target, jstring path, jobject dest, jobject result) {
    jclass destClass = env->GetObjectClass(dest);
    jmethodID mid = env->GetMethodID(destClass, "details", "(IJJ)V");
    if (mid == NULL) {
        mark_failed_with_message(env, "could not find method", result);
        return;
    }

    WIN32_FILE_ATTRIBUTE_DATA attr;
    wchar_t* pathStr = java_to_wchar(env, path, result);
    BOOL ok = GetFileAttributesExW(pathStr, GetFileExInfoStandard, &attr);
    free(pathStr);
    if (!ok) {
        DWORD error = GetLastError();
        if (error == ERROR_FILE_NOT_FOUND || error == ERROR_PATH_NOT_FOUND || error == ERROR_NOT_READY) {
            // Treat device with no media as missing
            env->CallVoidMethod(dest, mid, (jint)FILE_TYPE_MISSING, (jlong)0, (jlong)0);
            return;
        }
        mark_failed_with_errno(env, "could not file attributes", result);
        return;
    }
    jlong lastModified = lastModifiedNanos(&attr.ftLastWriteTime);
    if (attr.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
        env->CallVoidMethod(dest, mid, (jint)FILE_TYPE_DIRECTORY, (jlong)0, lastModified);
    } else {
        jlong size = ((jlong)attr.nFileSizeHigh << 32) | attr.nFileSizeLow;
        env->CallVoidMethod(dest, mid, (jint)FILE_TYPE_FILE, size, lastModified);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsFileFunctions_readdir(JNIEnv *env, jclass target, jstring path, jobject contents, jobject result) {
    jclass contentsClass = env->GetObjectClass(contents);
    jmethodID mid = env->GetMethodID(contentsClass, "addFile", "(Ljava/lang/String;IJJ)V");
    if (mid == NULL) {
        mark_failed_with_message(env, "could not find method", result);
        return;
    }

    WIN32_FIND_DATAW entry;
    wchar_t* pathStr = java_to_wchar(env, path, result);
    HANDLE dirHandle = FindFirstFileW(pathStr, &entry);
    free(pathStr);
    if (dirHandle == INVALID_HANDLE_VALUE) {
        mark_failed_with_errno(env, "could not open directory", result);
        return;
    }

    do {
        if (wcscmp(L".", entry.cFileName) == 0 || wcscmp(L"..", entry.cFileName) == 0) {
            continue;
        }
        jstring childName = wchar_to_java(env, entry.cFileName, wcslen(entry.cFileName), result);
        jint type = (entry.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) ? FILE_TYPE_DIRECTORY : FILE_TYPE_FILE;
        jlong lastModified = lastModifiedNanos(&entry.ftLastWriteTime);
        jlong size = ((jlong)entry.nFileSizeHigh << 32) | entry.nFileSizeLow;
        env->CallVoidMethod(contents, mid, childName, type, size, lastModified);
    } while (FindNextFileW(dirHandle, &entry) != 0);

    DWORD error = GetLastError();
    if (error != ERROR_NO_MORE_FILES ) {
        mark_failed_with_errno(env, "could not read next directory entry", result);
    }

    FindClose(dirHandle);
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
        if (GetLastError() != ERROR_INVALID_PARAMETER && GetLastError() != ERROR_INVALID_HANDLE) {
            mark_failed_with_errno(env, "could not change std handle", result);
        }
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

HKEY get_key_from_ordinal(jint keyNum) {
    return keyNum == 0 ? HKEY_LOCAL_MACHINE : HKEY_CURRENT_USER;
}

JNIEXPORT jstring JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsRegistryFunctions_getStringValue(JNIEnv *env, jclass target, jint keyNum, jstring subkey, jstring valueName, jobject result) {
    HKEY key = get_key_from_ordinal(keyNum);
    wchar_t* subkeyStr = java_to_wchar(env, subkey, result);
    wchar_t* valueNameStr = java_to_wchar(env, valueName, result);
    DWORD size = 0;

    LONG retval = SHRegGetValueW(key, subkeyStr, valueNameStr, SRRF_RT_REG_SZ, NULL, NULL, &size);
    if (retval != ERROR_SUCCESS) {
        free(subkeyStr);
        free(valueNameStr);
        if (retval != ERROR_FILE_NOT_FOUND) {
            mark_failed_with_code(env, "could not determine size of registry value", retval, NULL, result);
        }
        return NULL;
    }

    wchar_t* value = (wchar_t*)malloc(sizeof(wchar_t) * (size+1));
    retval = SHRegGetValueW(key, subkeyStr, valueNameStr, SRRF_RT_REG_SZ, NULL, value, &size);
    free(subkeyStr);
    free(valueNameStr);
    if (retval != ERROR_SUCCESS) {
        free(value);
        mark_failed_with_code(env, "could not get registry value", retval, NULL, result);
        return NULL;
    }

    jstring jvalue = wchar_to_java(env, value, wcslen(value), result);
    free(value);

    return jvalue;
}

JNIEXPORT jboolean JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsRegistryFunctions_getSubkeys(JNIEnv *env, jclass target, jint keyNum, jstring subkey, jobject subkeys, jobject result) {
    wchar_t* subkeyStr = java_to_wchar(env, subkey, result);
    jclass subkeys_class = env->GetObjectClass(subkeys);
    jmethodID method = env->GetMethodID(subkeys_class, "add", "(Ljava/lang/Object;)Z");

    HKEY key;
    LONG retval = RegOpenKeyExW(get_key_from_ordinal(keyNum), subkeyStr, 0, KEY_READ, &key);
    if (retval != ERROR_SUCCESS) {
        free(subkeyStr);
        if (retval != ERROR_FILE_NOT_FOUND) {
            mark_failed_with_code(env, "could open registry key", retval, NULL, result);
        }
        return false;
    }

    DWORD subkeyCount;
    DWORD maxSubkeyLen;
    retval = RegQueryInfoKeyW(key, NULL, NULL, NULL, &subkeyCount, &maxSubkeyLen, NULL, NULL, NULL, NULL, NULL, NULL);
    if (retval != ERROR_SUCCESS) {
        mark_failed_with_code(env, "could query registry key", retval, NULL, result);
    } else {
        wchar_t* keyNameStr = (wchar_t*)malloc(sizeof(wchar_t) * (maxSubkeyLen+1));
        for (int i = 0; i < subkeyCount; i++) {
            DWORD keyNameLen = maxSubkeyLen + 1;
            retval = RegEnumKeyExW(key, i, keyNameStr, &keyNameLen, NULL, NULL, NULL, NULL);
            if (retval != ERROR_SUCCESS) {
                mark_failed_with_code(env, "could enumerate registry subkey", retval, NULL, result);
                break;
            }
            env->CallVoidMethod(subkeys, method, wchar_to_java(env, keyNameStr, wcslen(keyNameStr), result));
        }
        free(keyNameStr);
    }

    RegCloseKey(key);
    free(subkeyStr);
    return true;
}

JNIEXPORT jboolean JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsRegistryFunctions_getValueNames(JNIEnv *env, jclass target, jint keyNum, jstring subkey, jobject names, jobject result) {
    wchar_t* subkeyStr = java_to_wchar(env, subkey, result);
    jclass names_class = env->GetObjectClass(names);
    jmethodID method = env->GetMethodID(names_class, "add", "(Ljava/lang/Object;)Z");

    HKEY key;
    LONG retval = RegOpenKeyExW(get_key_from_ordinal(keyNum), subkeyStr, 0, KEY_READ, &key);
    if (retval != ERROR_SUCCESS) {
        free(subkeyStr);
        if (retval != ERROR_FILE_NOT_FOUND) {
            mark_failed_with_code(env, "could open registry key", retval, NULL, result);
        }
        return false;
    }

    DWORD valueCount;
    DWORD maxValueNameLen;
    retval = RegQueryInfoKeyW(key, NULL, NULL, NULL, NULL, NULL, NULL, &valueCount, &maxValueNameLen, NULL, NULL, NULL);
    if (retval != ERROR_SUCCESS) {
        mark_failed_with_code(env, "could query registry key", retval, NULL, result);
    } else {
        wchar_t* valueNameStr = (wchar_t*)malloc(sizeof(wchar_t) * (maxValueNameLen+1));
        for (int i = 0; i < valueCount; i++) {
            DWORD valueNameLen = maxValueNameLen + 1;
            retval = RegEnumValueW(key, i, valueNameStr, &valueNameLen, NULL, NULL, NULL, NULL);
            if (retval != ERROR_SUCCESS) {
                mark_failed_with_code(env, "could enumerate registry value name", retval, NULL, result);
                break;
            }
            env->CallVoidMethod(names, method, wchar_to_java(env, valueNameStr, wcslen(valueNameStr), result));
        }
        free(valueNameStr);
    }

    RegCloseKey(key);
    free(subkeyStr);
    return true;
}

#endif
