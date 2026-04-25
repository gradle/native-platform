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

#include "win.h"
#include "generic.h"
#include "net_rubygrapefruit_platform_internal_jni_WindowsMemoryFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_NativeLibraryFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_PosixFileSystemFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_WindowsFileFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_WindowsHandleFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_WindowsRegistryFunctions.h"

#define ALL_COLORS (FOREGROUND_BLUE | FOREGROUND_RED | FOREGROUND_GREEN)

/*
 * Marks the given result as failed, using the current value of GetLastError()
 */
void mark_failed_with_errno(JNIEnv* env, const char* message, jobject result) {
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

//
// Returns 'true' if a file, given its attributes, is a Windows Symbolic Link.
//
bool is_file_symlink(DWORD dwFileAttributes, DWORD reparseTagData) {
    //
    // See https://docs.microsoft.com/en-us/windows/desktop/fileio/reparse-point-tags
    //  IO_REPARSE_TAG_SYMLINK (0xA000000C)
    //
    return ((dwFileAttributes & FILE_ATTRIBUTE_REPARSE_POINT) == FILE_ATTRIBUTE_REPARSE_POINT)
        && (reparseTagData == IO_REPARSE_TAG_SYMLINK);
}

jlong lastModifiedNanos(FILETIME* time) {
    return ((jlong) time->dwHighDateTime << 32) | time->dwLowDateTime;
}

void fillFileStat(file_stat_t* pFileStat, bool symlink, DWORD attributes, FILETIME* ftLastWriteTime, DWORD nFileSizeHigh, DWORD nFileSizeLow) {
    pFileStat->lastModified = lastModifiedNanos(ftLastWriteTime);
    if (symlink) {
        pFileStat->fileType = FILE_TYPE_SYMLINK;
        pFileStat->size = 0;
    } else if (attributes & FILE_ATTRIBUTE_DIRECTORY) {
        pFileStat->fileType = FILE_TYPE_DIRECTORY;
        pFileStat->size = 0;
    } else {
        pFileStat->size = ((jlong) nFileSizeHigh << 32) | nFileSizeLow;
        pFileStat->fileType = FILE_TYPE_FILE;
    }
}

//
// Retrieves the file attributes for the file specified by |pathStr|.
// If |followLink| is true, symbolic link targets are resolved.
//
// * Returns ERROR_SUCCESS if the file exists and file attributes can be retrieved,
// * Returns ERROR_SUCCESS with a FILE_TYPE_MISSING if the file does not exist,
// * Returns a Win32 error code in all other cases.
//
DWORD get_file_stat(wchar_t* pathStr, jboolean followLink, file_stat_t* pFileStat) {
    WIN32_FILE_ATTRIBUTE_DATA attr;
    BOOL ok = GetFileAttributesExW(pathStr, GetFileExInfoStandard, &attr);
    if (!ok) {
        DWORD error = GetLastError();
        if (error == ERROR_FILE_NOT_FOUND || error == ERROR_PATH_NOT_FOUND || error == ERROR_NOT_READY) {
            // Treat device with no media as missing
            pFileStat->lastModified = 0;
            pFileStat->size = 0;
            pFileStat->fileType = FILE_TYPE_MISSING;
            return ERROR_SUCCESS;
        }
        return error;
    }
#ifdef WINDOWS_MIN
    // Done, no Symlinks
    fillFileStat(
        pFileStat,
        false,
        attr.dwFileAttributes,
        &attr.ftLastWriteTime,
        attr.nFileSizeHigh,
        attr.nFileSizeLow);
    return ERROR_SUCCESS;
#else //WINDOWS_MIN: Windows Vista+ support for symlinks
    if ((attr.dwFileAttributes & FILE_ATTRIBUTE_REPARSE_POINT) != FILE_ATTRIBUTE_REPARSE_POINT) {
        // Done, not a symlink
        fillFileStat(
            pFileStat,
            false,
            attr.dwFileAttributes,
            &attr.ftLastWriteTime,
            attr.nFileSizeHigh,
            attr.nFileSizeLow);
        return ERROR_SUCCESS;
    }

    // Now let's try to follow the symlink or find out if the current reparse point is a symlink
    DWORD dwFlagsAndAttributes = FILE_FLAG_BACKUP_SEMANTICS;
    if (!followLink) {
        dwFlagsAndAttributes |= FILE_FLAG_OPEN_REPARSE_POINT;
    }
    HANDLE fileHandle = CreateFileW(
        pathStr,                                                   // lpFileName
        GENERIC_READ,                                              // dwDesiredAccess
        FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,    // dwShareMode
        NULL,                                                      // lpSecurityAttributes
        OPEN_EXISTING,                                             // dwCreationDisposition
        dwFlagsAndAttributes,                                      // dwFlagsAndAttributes
        NULL                                                       // hTemplateFile
    );
    if (fileHandle == INVALID_HANDLE_VALUE) {
        DWORD error = GetLastError();
        if (error == ERROR_FILE_NOT_FOUND || error == ERROR_PATH_NOT_FOUND || error == ERROR_NOT_READY) {
            // Treat device with no media as missing
            pFileStat->lastModified = 0;
            pFileStat->size = 0;
            pFileStat->fileType = FILE_TYPE_MISSING;
            return ERROR_SUCCESS;
        }
        return error;
    }

    // This call allows retrieving almost everything except for the reparseTag
    BY_HANDLE_FILE_INFORMATION fileInfo;
    ok = GetFileInformationByHandle(fileHandle, &fileInfo);
    if (!ok) {
        DWORD error = GetLastError();
        CloseHandle(fileHandle);
        return error;
    }

    if ((fileInfo.dwFileAttributes & FILE_ATTRIBUTE_REPARSE_POINT) != FILE_ATTRIBUTE_REPARSE_POINT) {
        fillFileStat(
            pFileStat,
            false,
            fileInfo.dwFileAttributes,
            &fileInfo.ftLastWriteTime,
            fileInfo.nFileSizeHigh,
            fileInfo.nFileSizeLow);
        CloseHandle(fileHandle);
        return ERROR_SUCCESS;
    }

    // This call allows retrieving the reparse tag
    // It appears calling GetFileInformationByHandleEx with FILE_ATTRIBUTE_TAG_INFO
    // fails on FAT file system with ERROR_INVALID_PARAMETER.
    // Since here we already know that we have a reparse point, we are not on FAT.
    FILE_ATTRIBUTE_TAG_INFO fileTagInfo;
    ok = GetFileInformationByHandleEx(fileHandle, FileAttributeTagInfo, &fileTagInfo, sizeof(fileTagInfo));

    if (!ok) {
        DWORD error = GetLastError();
        CloseHandle(fileHandle);
        return error;
    }

    CloseHandle(fileHandle);

    fillFileStat(
        pFileStat,
        is_file_symlink(fileTagInfo.FileAttributes, fileTagInfo.ReparseTag),
        fileTagInfo.FileAttributes,
        &fileInfo.ftLastWriteTime,
        fileInfo.nFileSizeHigh,
        fileInfo.nFileSizeLow);
    return ERROR_SUCCESS;
#endif
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_NativeLibraryFunctions_getSystemInfo(JNIEnv* env, jclass target, jobject info, jobject result) {
    jclass infoClass = env->GetObjectClass(info);

    OSVERSIONINFOEX versionInfo;
    versionInfo.dwOSVersionInfoSize = sizeof(OSVERSIONINFOEX);
    if (GetVersionEx((OSVERSIONINFO*) &versionInfo) == 0) {
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
    } else if (systemInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_ARM64) {
        arch = env->NewStringUTF("arm64");
    } else {
        arch = env->NewStringUTF("unknown");
    }

    jstring hostname = NULL;
    DWORD cnSize = MAX_COMPUTERNAME_LENGTH + 1;
    wchar_t* computerName = (wchar_t*) malloc(sizeof(wchar_t) * cnSize);
    if (GetComputerNameW(computerName, &cnSize)) {
        hostname = wchar_to_java(env, computerName, cnSize, result);
    }
    free(computerName);

    jmethodID method = env->GetMethodID(infoClass, "windows", "(IIIZLjava/lang/String;Ljava/lang/String;)V");
    env->CallVoidMethod(info, method, versionInfo.dwMajorVersion, versionInfo.dwMinorVersion,
        versionInfo.dwBuildNumber, versionInfo.wProductType == VER_NT_WORKSTATION,
        arch, hostname);
}

/*
 * Process functions
 */

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getPid(JNIEnv* env, jclass target) {
    return GetCurrentProcessId();
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_detach(JNIEnv* env, jclass target, jobject result) {
    if (FreeConsole() == 0) {
        // Ignore if the error is that the process is already detached from the console
        if (GetLastError() != ERROR_INVALID_PARAMETER) {
            mark_failed_with_errno(env, "could not FreeConsole()", result);
        }
    }
}

JNIEXPORT jstring JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getWorkingDirectory(JNIEnv* env, jclass target, jobject result) {
    DWORD size = GetCurrentDirectoryW(0, NULL);
    if (size == 0) {
        mark_failed_with_errno(env, "could not determine length of working directory path", result);
        return NULL;
    }
    size = size + 1;    // Needs to include null character
    wchar_t* path = (wchar_t*) malloc(sizeof(wchar_t) * size);
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
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_setWorkingDirectory(JNIEnv* env, jclass target, jstring dir, jobject result) {
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
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getEnvironmentVariable(JNIEnv* env, jclass target, jstring var, jobject result) {
    wchar_t* varStr = java_to_wchar(env, var, result);
    DWORD len = GetEnvironmentVariableW(varStr, NULL, 0);
    if (len == 0) {
        if (GetLastError() != ERROR_ENVVAR_NOT_FOUND) {
            mark_failed_with_errno(env, "could not determine length of environment variable", result);
        }
        free(varStr);
        return NULL;
    }
    wchar_t* valueStr = (wchar_t*) malloc(sizeof(wchar_t) * len);
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
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_setEnvironmentVariable(JNIEnv* env, jclass target, jstring var, jstring value, jobject result) {
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
Java_net_rubygrapefruit_platform_internal_jni_PosixFileSystemFunctions_listFileSystems(JNIEnv* env, jclass target, jobject info, jobject result) {
    jclass info_class = env->GetObjectClass(info);
    jmethodID method = env->GetMethodID(info_class, "add", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZZ)V");
    jmethodID unknownFsMethod = env->GetMethodID(info_class, "addForUnknownCaseSensitivity", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V");

    DWORD required = GetLogicalDriveStringsW(0, NULL);
    if (required == 0) {
        mark_failed_with_errno(env, "could not determine logical drive buffer size", result);
        return;
    }

    wchar_t* buffer = (wchar_t*) malloc(sizeof(wchar_t) * (required + 1));
    wchar_t* deviceName = (wchar_t*) malloc(sizeof(wchar_t) * (MAX_PATH + 1));
    wchar_t* fileSystemName = (wchar_t*) malloc(sizeof(wchar_t) * (MAX_PATH + 1));

    if (GetLogicalDriveStringsW(required, buffer) == 0) {
        mark_failed_with_errno(env, "could not determine logical drives", result);
    } else {
        wchar_t* cur = buffer;
        for (; cur[0] != L'\0'; cur += wcslen(cur) + 1) {
            DWORD type = GetDriveTypeW(cur);
            jboolean remote = type == DRIVE_REMOTE;

            // chop off trailing '\'
            size_t len = wcslen(cur);
            cur[len - 1] = L'\0';

            // create device name \\.\C:
            wchar_t devPath[7];
            swprintf(devPath, 7, L"\\\\.\\%s", cur);

            if (QueryDosDeviceW(cur, deviceName, MAX_PATH + 1) == 0) {
                mark_failed_with_errno(env, "could not map device for logical drive", result);
                break;
            }
            cur[len - 1] = L'\\';

            DWORD available = 1;
            if (!remote) {
                HANDLE hDevice = CreateFileW(devPath,                          // like "\\.\E:"
                    FILE_READ_ATTRIBUTES,                                      // read access to the attributes
                    FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,    // share mode
                    NULL, OPEN_EXISTING, 0, NULL);
                if (hDevice != INVALID_HANDLE_VALUE) {
                    DWORD cbBytesReturned;
                    DWORD bSuccess = DeviceIoControl(hDevice,    // device to be queried
                        IOCTL_STORAGE_CHECK_VERIFY2,
                        NULL, 0,                 // no input buffer
                        NULL, 0,                 // no output buffer
                        &cbBytesReturned,        // # bytes returned
                        (LPOVERLAPPED) NULL);    // synchronous I/O
                    if (!bSuccess) {
                        available = 0;
                    }
                    CloseHandle(hDevice);
                }
            }

            jstring mount_point = wchar_to_java(env, cur, wcslen(cur), result);
            jstring device_name = wchar_to_java(env, deviceName, wcslen(deviceName), result);

            jboolean casePreserving = JNI_TRUE;
            if (available) {
                DWORD flags;
                if (GetVolumeInformationW(cur, NULL, 0, NULL, NULL, &flags, fileSystemName, MAX_PATH + 1) == 0) {
                    env->CallVoidMethod(info, unknownFsMethod,
                        mount_point,
                        NULL,
                        device_name,
                        remote);
                    continue;
                }
                casePreserving = (flags & FILE_CASE_PRESERVED_NAMES) != 0;
            } else {
                if (type == DRIVE_CDROM) {
                    swprintf(fileSystemName, MAX_PATH + 1, L"cdrom");
                } else {
                    swprintf(fileSystemName, MAX_PATH + 1, L"unknown");
                }
            }

            jstring file_system_type = wchar_to_java(env, fileSystemName, wcslen(fileSystemName), result);
            env->CallVoidMethod(info, method,
                mount_point,
                file_system_type,
                device_name,
                remote, JNI_FALSE, casePreserving);
        }
    }

    free(buffer);
    free(deviceName);
    free(fileSystemName);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsFileFunctions_stat(JNIEnv* env, jclass target, jstring path, jboolean followLink, jobject dest, jobject result) {
    jclass destClass = env->GetObjectClass(dest);
    jmethodID mid = env->GetMethodID(destClass, "details", "(IJJ)V");
    if (mid == NULL) {
        mark_failed_with_message(env, "could not find method", result);
        return;
    }

    wchar_t* pathStr = java_to_wchar_path(env, path);
    file_stat_t fileStat;
    DWORD errorCode = get_file_stat(pathStr, followLink, &fileStat);
    free(pathStr);
    if (errorCode != ERROR_SUCCESS) {
        mark_failed_with_code(env, "could not file attributes", errorCode, NULL, result);
        return;
    }
    env->CallVoidMethod(dest, mid, fileStat.fileType, fileStat.size, fileStat.lastModified);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsFileFunctions_readdir(JNIEnv* env, jclass target, jstring path, jboolean followLink, jobject contents, jobject result) {
    jclass contentsClass = env->GetObjectClass(contents);
    jmethodID mid = env->GetMethodID(contentsClass, "addFile", "(Ljava/lang/String;IJJ)V");
    if (mid == NULL) {
        mark_failed_with_message(env, "could not find method", result);
        return;
    }

    WIN32_FIND_DATAW entry;
    wchar_t* pathStr = java_to_wchar_path(env, path);
    wchar_t* patternStr = add_suffix(pathStr, wcslen(pathStr), L"\\*");
    free(pathStr);
    HANDLE dirHandle = FindFirstFileW(patternStr, &entry);
    if (dirHandle == INVALID_HANDLE_VALUE) {
        mark_failed_with_errno(env, "could not open directory", result);
        free(patternStr);
        return;
    }

    do {
        if (wcscmp(L".", entry.cFileName) == 0 || wcscmp(L"..", entry.cFileName) == 0) {
            continue;
        }

        // If entry is a symbolic link, we may have to get the attributes of the link target
        bool isSymLink = is_file_symlink(entry.dwFileAttributes, entry.dwReserved0);
        file_stat_t fileInfo;
        if (isSymLink && followLink) {
            // We use patternStr minus the last character ("*") to create the absolute path of the child entry
            wchar_t* childPathStr = add_suffix(patternStr, wcslen(patternStr) - 1, entry.cFileName);
            DWORD errorCode = get_file_stat(childPathStr, true, &fileInfo);
            free(childPathStr);
            if (errorCode != ERROR_SUCCESS) {
                mark_failed_with_errno(env, "could not stat directory entry", result);
                break;
            }
        } else {
            fileInfo.fileType = isSymLink
                ? FILE_TYPE_SYMLINK
                : (entry.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)
                    ? FILE_TYPE_DIRECTORY
                    : FILE_TYPE_FILE;
            fileInfo.lastModified = lastModifiedNanos(&entry.ftLastWriteTime);
            fileInfo.size = ((jlong) entry.nFileSizeHigh << 32) | entry.nFileSizeLow;
        }

        // Add entry
        jstring childName = wchar_to_java(env, entry.cFileName, wcslen(entry.cFileName), result);
        env->CallVoidMethod(contents, mid, childName, fileInfo.fileType, fileInfo.size, fileInfo.lastModified);
    } while (FindNextFileW(dirHandle, &entry) != 0);

    DWORD error = GetLastError();
    if (error != ERROR_NO_MORE_FILES) {
        mark_failed_with_errno(env, "could not read next directory entry", result);
    }

    free(patternStr);
    FindClose(dirHandle);
}

/*
 * Console functions
 */

HANDLE getHandle(JNIEnv* env, int output, jobject result) {
    HANDLE handle = INVALID_HANDLE_VALUE;
    switch (output) {
        case STDIN_DESCRIPTOR:
            handle = GetStdHandle(STD_INPUT_HANDLE);
            break;
        case STDOUT_DESCRIPTOR:
            handle = GetStdHandle(STD_OUTPUT_HANDLE);
            break;
        case STDERR_DESCRIPTOR:
            handle = GetStdHandle(STD_ERROR_HANDLE);
            break;
    }
    if (handle == INVALID_HANDLE_VALUE) {
        mark_failed_with_errno(env, "could not get console handle", result);
        return NULL;
    }
    return handle;
}

#define CONSOLE_NONE 0
#define CONSOLE_WINDOWS 1
#define CONSOLE_CYGWIN 2

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_isConsole(JNIEnv* env, jclass target, jint output, jobject result) {
    CONSOLE_SCREEN_BUFFER_INFO console_info;
    HANDLE handle = getHandle(env, output, result);
    if (handle == NULL) {
        return CONSOLE_NONE;
    }

#ifndef WINDOWS_MIN
    // Cygwin/msys console detection, uses an API not available on older Windows versions
    // Look for a named pipe at the output or input handle, with a specific name:
    // Cygwin: \cygwin-xxxx-from-master (stdin)
    // Cygwin: \cygwin-xxxx-to-master (stdout/stderr)
    // Msys: \msys-xxxx-from-master (stdin)
    // Msys: \msys-xxxx-to-master (stdout/stderr)
    DWORD type = GetFileType(handle);
    if (type == FILE_TYPE_PIPE) {
        size_t size = sizeof(_FILE_NAME_INFO) + MAX_PATH * sizeof(WCHAR);
        _FILE_NAME_INFO* name_info = (_FILE_NAME_INFO*) malloc(size);

        if (GetFileInformationByHandleEx(handle, FileNameInfo, name_info, size) == 0) {
            mark_failed_with_errno(env, "could not get handle file information", result);
            free(name_info);
            return CONSOLE_NONE;
        }

        ((char*) name_info->FileName)[name_info->FileNameLength] = 0;

        int consoleType = CONSOLE_NONE;
        if (wcsstr(name_info->FileName, L"\\cygwin-") == name_info->FileName || wcsstr(name_info->FileName, L"\\msys-") == name_info->FileName) {
            if (output == STDIN_DESCRIPTOR) {
                if (wcsstr(name_info->FileName, L"-from-master") != NULL) {
                    consoleType = CONSOLE_CYGWIN;
                }
            } else {
                if (wcsstr(name_info->FileName, L"-to-master") != NULL) {
                    consoleType = CONSOLE_CYGWIN;
                }
            }
        }
        free(name_info);
        return consoleType;
    }
#endif    // Else, no Cygwin console detection

    if (output == STDIN_DESCRIPTOR) {
        DWORD mode;
        if (!GetConsoleMode(handle, &mode)) {
            if (GetLastError() != ERROR_INVALID_HANDLE) {
                mark_failed_with_errno(env, "could not get console buffer", result);
            }
            return CONSOLE_NONE;
        }
        return CONSOLE_WINDOWS;
    }
    if (!GetConsoleScreenBufferInfo(handle, &console_info)) {
        if (GetLastError() != ERROR_INVALID_HANDLE) {
            mark_failed_with_errno(env, "could not get console buffer", result);
        }
        return CONSOLE_NONE;
    }
    return CONSOLE_WINDOWS;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_getConsoleSize(JNIEnv* env, jclass target, jint output, jobject dimension, jobject result) {
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

HANDLE console_buffer = NULL;
DWORD original_mode = 0;

void init_input(JNIEnv* env, jobject result) {
    if (console_buffer == NULL) {
        console_buffer = GetStdHandle(STD_INPUT_HANDLE);
        if (!GetConsoleMode(console_buffer, &original_mode)) {
            mark_failed_with_errno(env, "could not get console buffer mode", result);
            return;
        }
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_rawInputMode(JNIEnv* env, jclass target, jobject result) {
    init_input(env, result);
    DWORD mode = original_mode & ~(ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT);
    if (!SetConsoleMode(console_buffer, mode)) {
        mark_failed_with_errno(env, "could not set console buffer mode", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_resetInputMode(JNIEnv* env, jclass target, jobject result) {
    if (console_buffer == NULL) {
        return;
    }
    if (!SetConsoleMode(console_buffer, original_mode)) {
        mark_failed_with_errno(env, "could not set console buffer mode", result);
    }
}

void control_key(JNIEnv* env, jint key, jobject char_buffer, jobject result) {
    jclass bufferClass = env->GetObjectClass(char_buffer);
    jmethodID method = env->GetMethodID(bufferClass, "key", "(I)V");
    env->CallVoidMethod(char_buffer, method, key);
}

void character(JNIEnv* env, jchar char_value, jobject char_buffer, jobject result) {
    jclass bufferClass = env->GetObjectClass(char_buffer);
    jmethodID method = env->GetMethodID(bufferClass, "character", "(C)V");
    env->CallVoidMethod(char_buffer, method, char_value);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_readInput(JNIEnv* env, jclass target, jobject char_buffer, jobject result) {
    init_input(env, result);
    INPUT_RECORD events[1];
    DWORD nread;
    while (TRUE) {
        if (!ReadConsoleInputW(console_buffer, events, 1, &nread)) {
            mark_failed_with_errno(env, "could not read from console", result);
            return;
        }
        if (events[0].EventType != KEY_EVENT) {
            continue;
        }
        KEY_EVENT_RECORD keyEvent = events[0].Event.KeyEvent;
        if (!keyEvent.bKeyDown) {
            if (keyEvent.wVirtualKeyCode == 0x43 && keyEvent.uChar.UnicodeChar == 3) {
                // key down event for ctrl-c doesn't seem to be delivered, but key up event does
                return;
            }
            continue;
        }

        if ((keyEvent.dwControlKeyState & (LEFT_ALT_PRESSED | LEFT_CTRL_PRESSED | RIGHT_ALT_PRESSED | RIGHT_CTRL_PRESSED | SHIFT_PRESSED)) == 0) {
            if (keyEvent.wVirtualKeyCode == VK_RETURN) {
                control_key(env, 0, char_buffer, result);
                return;
            } else if (keyEvent.wVirtualKeyCode == VK_UP) {
                control_key(env, 1, char_buffer, result);
                return;
            } else if (keyEvent.wVirtualKeyCode == VK_DOWN) {
                control_key(env, 2, char_buffer, result);
                return;
            } else if (keyEvent.wVirtualKeyCode == VK_LEFT) {
                control_key(env, 3, char_buffer, result);
                return;
            } else if (keyEvent.wVirtualKeyCode == VK_RIGHT) {
                control_key(env, 4, char_buffer, result);
                return;
            } else if (keyEvent.wVirtualKeyCode == VK_HOME) {
                control_key(env, 5, char_buffer, result);
                return;
            } else if (keyEvent.wVirtualKeyCode == VK_END) {
                control_key(env, 6, char_buffer, result);
                return;
            } else if (keyEvent.wVirtualKeyCode == VK_BACK) {
                control_key(env, 7, char_buffer, result);
                return;
            } else if (keyEvent.wVirtualKeyCode == VK_DELETE) {
                control_key(env, 8, char_buffer, result);
                return;
            } else if (keyEvent.wVirtualKeyCode == VK_PRIOR) {    // page up
                control_key(env, 10, char_buffer, result);
                return;
            } else if (keyEvent.wVirtualKeyCode == VK_NEXT) {    // page down
                control_key(env, 11, char_buffer, result);
                return;
            }
        }
        if (keyEvent.wVirtualKeyCode == 0x44 && keyEvent.uChar.UnicodeChar == 4) {
            // ctrl-d
            return;
        }
        if (keyEvent.uChar.UnicodeChar == 0) {
            // Some other control key
            continue;
        }
        if (keyEvent.uChar.UnicodeChar == '\t' && (keyEvent.dwControlKeyState & (SHIFT_PRESSED)) == 0) {
            // shift-tab
            control_key(env, 9, char_buffer, result);
        } else {
            character(env, (jchar) keyEvent.uChar.UnicodeChar, char_buffer, result);
        }
        return;
    }
}

HANDLE current_console = NULL;
WORD original_attributes = 0;
WORD current_attributes = 0;
CONSOLE_CURSOR_INFO original_cursor;

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_initConsole(JNIEnv* env, jclass target, jint output, jobject result) {
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
    if (!GetConsoleCursorInfo(handle, &original_cursor)) {
        mark_failed_with_errno(env, "could not get console cursor", result);
        return;
    }
    current_console = handle;
    original_attributes = console_info.wAttributes;
    current_attributes = original_attributes;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_boldOn(JNIEnv* env, jclass target, jobject result) {
    current_attributes |= FOREGROUND_INTENSITY;
    if (!SetConsoleTextAttribute(current_console, current_attributes)) {
        mark_failed_with_errno(env, "could not set text attributes", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_boldOff(JNIEnv* env, jclass target, jobject result) {
    current_attributes &= ~FOREGROUND_INTENSITY;
    if (!SetConsoleTextAttribute(current_console, current_attributes)) {
        mark_failed_with_errno(env, "could not set text attributes", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_reset(JNIEnv* env, jclass target, jobject result) {
    current_attributes = original_attributes;
    if (!SetConsoleTextAttribute(current_console, current_attributes)) {
        mark_failed_with_errno(env, "could not set text attributes", result);
    }
    if (!SetConsoleCursorInfo(current_console, &original_cursor)) {
        mark_failed_with_errno(env, "could not set console cursor", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_foreground(JNIEnv* env, jclass target, jint color, jobject result) {
    current_attributes &= ~ALL_COLORS;
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
            current_attributes |= FOREGROUND_RED | FOREGROUND_GREEN;
            break;
        case 4:
            current_attributes |= FOREGROUND_BLUE;
            break;
        case 5:
            current_attributes |= FOREGROUND_RED | FOREGROUND_BLUE;
            break;
        case 6:
            current_attributes |= FOREGROUND_GREEN | FOREGROUND_BLUE;
            break;
        default:
            current_attributes |= FOREGROUND_RED | FOREGROUND_GREEN | FOREGROUND_BLUE;
            break;
    }

    if (!SetConsoleTextAttribute(current_console, current_attributes)) {
        mark_failed_with_errno(env, "could not set text attributes", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_defaultForeground(JNIEnv* env, jclass target, jobject result) {
    current_attributes = (current_attributes & ~ALL_COLORS) | (original_attributes & ALL_COLORS);
    if (!SetConsoleTextAttribute(current_console, current_attributes)) {
        mark_failed_with_errno(env, "could not set text attributes", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_hideCursor(JNIEnv* env, jclass target, jobject result) {
    CONSOLE_CURSOR_INFO cursor;
    cursor = original_cursor;
    cursor.bVisible = false;
    if (!SetConsoleCursorInfo(current_console, &cursor)) {
        mark_failed_with_errno(env, "could not hide cursor", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_showCursor(JNIEnv* env, jclass target, jobject result) {
    CONSOLE_CURSOR_INFO cursor;
    cursor = original_cursor;
    cursor.bVisible = true;
    if (!SetConsoleCursorInfo(current_console, &cursor)) {
        mark_failed_with_errno(env, "could not show cursor", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_left(JNIEnv* env, jclass target, jint count, jobject result) {
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
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_right(JNIEnv* env, jclass target, jint count, jobject result) {
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
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_up(JNIEnv* env, jclass target, jint count, jobject result) {
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
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_down(JNIEnv* env, jclass target, jint count, jobject result) {
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
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_startLine(JNIEnv* env, jclass target, jobject result) {
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
Java_net_rubygrapefruit_platform_internal_jni_WindowsConsoleFunctions_clearToEndOfLine(JNIEnv* env, jclass target, jobject result) {
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

void uninheritStream(JNIEnv* env, DWORD stdInputHandle, jobject result) {
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
Java_net_rubygrapefruit_platform_internal_jni_WindowsHandleFunctions_markStandardHandlesUninheritable(JNIEnv* env, jclass target, jobject result) {
    uninheritStream(env, STD_INPUT_HANDLE, result);
    uninheritStream(env, STD_OUTPUT_HANDLE, result);
    uninheritStream(env, STD_ERROR_HANDLE, result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsHandleFunctions_restoreStandardHandles(JNIEnv* env, jclass target, jobject result) {
}

HKEY get_key_from_ordinal(jint keyNum) {
    return keyNum == 0 ? HKEY_LOCAL_MACHINE : HKEY_CURRENT_USER;
}

JNIEXPORT jstring JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsRegistryFunctions_getStringValue(JNIEnv* env, jclass target, jint keyNum, jstring subkey, jstring valueName, jobject result) {
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

    wchar_t* value = (wchar_t*) malloc(sizeof(wchar_t) * (size + 1));
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
Java_net_rubygrapefruit_platform_internal_jni_WindowsRegistryFunctions_getSubkeys(JNIEnv* env, jclass target, jint keyNum, jstring subkey, jobject subkeys, jobject result) {
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
        wchar_t* keyNameStr = (wchar_t*) malloc(sizeof(wchar_t) * (maxSubkeyLen + 1));
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
Java_net_rubygrapefruit_platform_internal_jni_WindowsRegistryFunctions_getValueNames(JNIEnv* env, jclass target, jint keyNum, jstring subkey, jobject names, jobject result) {
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
        wchar_t* valueNameStr = (wchar_t*) malloc(sizeof(wchar_t) * (maxValueNameLen + 1));
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

/**
 * Memory functions
 */
JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsMemoryFunctions_getWindowsMemoryInfo(JNIEnv* env, jclass type, jobject dest, jobject result) {
    jclass destClass = env->GetObjectClass(dest);
    jmethodID mid = env->GetMethodID(destClass, "details", "(JJJJ)V");
    if (mid == NULL) {
        mark_failed_with_message(env, "could not find method", result);
        return;
    }

    // Get total/avail physical memory
    MEMORYSTATUSEX statex;
    statex.dwLength = sizeof(statex);
    if (!GlobalMemoryStatusEx(&statex)) {
        mark_failed_with_errno(env, "could not query memory size", result);
        return;
    }

    // Get commit details
    PERFORMANCE_INFORMATION perfInfo;
    perfInfo.cb = sizeof(perfInfo);
    if (!GetPerformanceInfo(&perfInfo, sizeof(perfInfo))) {
        mark_failed_with_errno(env, "could not query performance info", result);
        return;
    }

    size_t commitTotalInBytes;
    HRESULT resultVal = SizeTMult(perfInfo.CommitTotal, perfInfo.PageSize, &commitTotalInBytes);
    if (resultVal != S_OK) {
        if (resultVal != INTSAFE_E_ARITHMETIC_OVERFLOW) {
            mark_failed_with_message(env, "could not calculate commit size", result);
            return;
        }
        commitTotalInBytes = SIZE_MAX;
    }
    size_t commitLimitInBytes;
    resultVal = SizeTMult(perfInfo.CommitLimit, perfInfo.PageSize, &commitLimitInBytes);
    if (resultVal != S_OK) {
        if (resultVal != INTSAFE_E_ARITHMETIC_OVERFLOW) {
            mark_failed_with_message(env, "could not calculate commit limit", result);
            return;
        }
        commitLimitInBytes = SIZE_MAX;
    }

    // Feed Java with details
    env->CallVoidMethod(dest, mid,
        (jlong) commitTotalInBytes,
        (jlong) commitLimitInBytes,
        (jlong) statex.ullTotalPhys,
        (jlong) statex.ullAvailPhys
        );
}

/*
 * PTY (ConPTY) functions
 */

#ifndef WINDOWS_MIN

#ifndef PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE
#define PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE 0x00020016
#endif

typedef VOID* HPCON;
typedef HRESULT (WINAPI *PFN_CreatePseudoConsole)(COORD, HANDLE, HANDLE, DWORD, HPCON*);
typedef HRESULT (WINAPI *PFN_ResizePseudoConsole)(HPCON, COORD);
typedef VOID    (WINAPI *PFN_ClosePseudoConsole)(HPCON);

static PFN_CreatePseudoConsole pCreatePseudoConsole = NULL;
static PFN_ResizePseudoConsole pResizePseudoConsole = NULL;
static PFN_ClosePseudoConsole  pClosePseudoConsole  = NULL;
static volatile LONG conpty_resolved = 0;
static BOOL conpty_available = FALSE;

static BOOL resolve_conpty() {
    if (InterlockedCompareExchange(&conpty_resolved, 1, 0) == 0) {
        HMODULE kernel32 = GetModuleHandleW(L"kernel32.dll");
        if (kernel32 != NULL) {
            pCreatePseudoConsole = (PFN_CreatePseudoConsole) GetProcAddress(kernel32, "CreatePseudoConsole");
            pResizePseudoConsole = (PFN_ResizePseudoConsole) GetProcAddress(kernel32, "ResizePseudoConsole");
            pClosePseudoConsole  = (PFN_ClosePseudoConsole)  GetProcAddress(kernel32, "ClosePseudoConsole");
            conpty_available = (pCreatePseudoConsole && pResizePseudoConsole && pClosePseudoConsole) ? TRUE : FALSE;
        }
    }
    return conpty_available;
}

JNIEXPORT jboolean JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_isConPtyAvailable(JNIEnv* env, jclass target) {
    return resolve_conpty() ? JNI_TRUE : JNI_FALSE;
}

static wchar_t* build_command_line(JNIEnv* env, jobjectArray command) {
    jsize argc = env->GetArrayLength(command);
    size_t total = 1;
    for (jsize i = 0; i < argc; i++) {
        jstring s = (jstring) env->GetObjectArrayElement(command, i);
        const jchar* chars = env->GetStringChars(s, NULL);
        jsize len = env->GetStringLength(s);
        bool needsQuote = false;
        for (jsize k = 0; k < len; k++) {
            jchar c = chars[k];
            if (c == L' ' || c == L'\t' || c == L'"') { needsQuote = true; break; }
        }
        if (len == 0) needsQuote = true;
        size_t entryLen = (size_t) len;
        if (needsQuote) entryLen += 2;
        for (jsize k = 0; k < len; k++) {
            if (chars[k] == L'"' || chars[k] == L'\\') entryLen += 1;
        }
        total += entryLen + 1;
        env->ReleaseStringChars(s, chars);
        env->DeleteLocalRef(s);
    }

    wchar_t* buffer = (wchar_t*) malloc(total * sizeof(wchar_t));
    if (buffer == NULL) return NULL;
    wchar_t* w = buffer;
    for (jsize i = 0; i < argc; i++) {
        if (i > 0) *w++ = L' ';
        jstring s = (jstring) env->GetObjectArrayElement(command, i);
        const jchar* chars = env->GetStringChars(s, NULL);
        jsize len = env->GetStringLength(s);
        bool needsQuote = (len == 0);
        for (jsize k = 0; k < len; k++) {
            jchar c = chars[k];
            if (c == L' ' || c == L'\t' || c == L'"') { needsQuote = true; break; }
        }
        if (needsQuote) *w++ = L'"';
        int backslashRun = 0;
        for (jsize k = 0; k < len; k++) {
            jchar c = chars[k];
            if (c == L'\\') {
                backslashRun++;
                *w++ = L'\\';
            } else if (c == L'"') {
                for (int bs = 0; bs < backslashRun; bs++) *w++ = L'\\';
                *w++ = L'\\';
                *w++ = L'"';
                backslashRun = 0;
            } else {
                *w++ = (wchar_t) c;
                backslashRun = 0;
            }
        }
        if (needsQuote) {
            for (int bs = 0; bs < backslashRun; bs++) *w++ = L'\\';
            *w++ = L'"';
        }
        env->ReleaseStringChars(s, chars);
        env->DeleteLocalRef(s);
    }
    *w = L'\0';
    return buffer;
}

static wchar_t* build_environment_block(JNIEnv* env, jobjectArray environment) {
    if (environment == NULL) return NULL;
    jsize envc = env->GetArrayLength(environment);
    if (envc == 0) return NULL;
    size_t total = 1;
    for (jsize i = 0; i < envc; i++) {
        jstring s = (jstring) env->GetObjectArrayElement(environment, i);
        total += (size_t) env->GetStringLength(s) + 1;
        env->DeleteLocalRef(s);
    }
    wchar_t* buffer = (wchar_t*) malloc(total * sizeof(wchar_t));
    if (buffer == NULL) return NULL;
    wchar_t* w = buffer;
    for (jsize i = 0; i < envc; i++) {
        jstring s = (jstring) env->GetObjectArrayElement(environment, i);
        const jchar* chars = env->GetStringChars(s, NULL);
        jsize len = env->GetStringLength(s);
        for (jsize k = 0; k < len; k++) {
            *w++ = (wchar_t) chars[k];
        }
        *w++ = L'\0';
        env->ReleaseStringChars(s, chars);
        env->DeleteLocalRef(s);
    }
    *w = L'\0';
    return buffer;
}

// Allocate the ConPTY plus its master-side pipes. We deliberately do NOT
// fork the child here — the Java caller must start a drainer thread on the
// returned ptyReadHandle BEFORE calling spawnConPtyProcess, otherwise
// ConPTY's startup VT noise can fill the output pipe and stall cmd.exe on
// its very first write.
JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_createPseudoConsole(
        JNIEnv* env, jclass target,
        jint cols, jint rows,
        jlongArray outHandles,
        jobject result) {
    if (!resolve_conpty()) {
        mark_failed_with_message(env, "ConPTY is not available on this Windows version", result);
        return;
    }

    HANDLE inputRead = INVALID_HANDLE_VALUE;
    HANDLE inputWrite = INVALID_HANDLE_VALUE;
    HANDLE outputRead = INVALID_HANDLE_VALUE;
    HANDLE outputWrite = INVALID_HANDLE_VALUE;
    HPCON hPC = NULL;
    BOOL ok = FALSE;

    SECURITY_ATTRIBUTES sa;
    sa.nLength = sizeof(sa);
    sa.lpSecurityDescriptor = NULL;
    sa.bInheritHandle = TRUE;

    do {
        if (!CreatePipe(&inputRead, &inputWrite, &sa, 65536)) {
            mark_failed_with_errno(env, "could not create ConPTY input pipe", result);
            break;
        }
        if (!CreatePipe(&outputRead, &outputWrite, &sa, 65536)) {
            mark_failed_with_errno(env, "could not create ConPTY output pipe", result);
            break;
        }
        // The master-side ends stay in this process; mark them non-inheritable
        // so a concurrent CreateProcess on another thread cannot leak them.
        SetHandleInformation(inputWrite, HANDLE_FLAG_INHERIT, 0);
        SetHandleInformation(outputRead, HANDLE_FLAG_INHERIT, 0);

        COORD size;
        size.X = (SHORT) cols;
        size.Y = (SHORT) rows;
        HRESULT hr = pCreatePseudoConsole(size, inputRead, outputWrite, 0, &hPC);
        if (FAILED(hr)) {
            mark_failed_with_code(env, "CreatePseudoConsole failed", (int) hr, NULL, result);
            break;
        }

        // ConPTY duplicates inputRead/outputWrite internally; the parent no
        // longer needs its own copy.
        CloseHandle(inputRead);  inputRead  = INVALID_HANDLE_VALUE;
        CloseHandle(outputWrite); outputWrite = INVALID_HANDLE_VALUE;

        jlong handles[4];
        handles[0] = (jlong) (intptr_t) hPC;
        handles[1] = (jlong) (intptr_t) outputRead;  // master reads child's stdout
        handles[2] = (jlong) (intptr_t) inputWrite;  // master writes to child's stdin
        handles[3] = 0;                              // stderr split not yet implemented
        env->SetLongArrayRegion(outHandles, 0, 4, handles);
        ok = TRUE;
    } while (0);

    if (!ok) {
        if (hPC != NULL) pClosePseudoConsole(hPC);
        if (inputRead != INVALID_HANDLE_VALUE) CloseHandle(inputRead);
        if (inputWrite != INVALID_HANDLE_VALUE) CloseHandle(inputWrite);
        if (outputRead != INVALID_HANDLE_VALUE) CloseHandle(outputRead);
        if (outputWrite != INVALID_HANDLE_VALUE) CloseHandle(outputWrite);
    }
}

// Attach a child process to an already-created ConPTY. The Java caller is
// expected to have started a drainer thread on the master read handle before
// calling this, see createPseudoConsole's contract.
JNIEXPORT jlong JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_spawnConPtyProcess(
        JNIEnv* env, jclass target,
        jlong hPCArg,
        jobjectArray command,
        jobjectArray environment,
        jstring workingDir,
        jlongArray outHandles,
        jobject result) {
    if (!resolve_conpty() || hPCArg == 0) {
        mark_failed_with_message(env, "ConPTY not available", result);
        return 0;
    }
    HPCON hPC = (HPCON) (intptr_t) hPCArg;

    LPPROC_THREAD_ATTRIBUTE_LIST attrList = NULL;
    SIZE_T attrListSize = 0;
    wchar_t* cmdLine = NULL;
    wchar_t* envBlock = NULL;
    wchar_t* wdStr = NULL;
    PROCESS_INFORMATION pi;
    ZeroMemory(&pi, sizeof(pi));
    BOOL processCreated = FALSE;
    BOOL ok = FALSE;

    do {
        cmdLine = build_command_line(env, command);
        if (cmdLine == NULL) {
            mark_failed_with_message(env, "could not build command line", result);
            break;
        }
        envBlock = build_environment_block(env, environment);
        if (workingDir != NULL) {
            wdStr = java_to_wchar(env, workingDir, result);
            if (wdStr == NULL) break;
        }

        InitializeProcThreadAttributeList(NULL, 1, 0, &attrListSize);
        attrList = (LPPROC_THREAD_ATTRIBUTE_LIST) malloc(attrListSize);
        if (attrList == NULL) {
            mark_failed_with_message(env, "could not allocate attribute list", result);
            break;
        }
        if (!InitializeProcThreadAttributeList(attrList, 1, 0, &attrListSize)) {
            mark_failed_with_errno(env, "InitializeProcThreadAttributeList failed", result);
            free(attrList); attrList = NULL;
            break;
        }
        if (!UpdateProcThreadAttribute(attrList, 0, PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE, hPC, sizeof(HPCON), NULL, NULL)) {
            mark_failed_with_errno(env, "UpdateProcThreadAttribute failed", result);
            break;
        }

        STARTUPINFOEXW siEx;
        ZeroMemory(&siEx, sizeof(siEx));
        siEx.StartupInfo.cb = sizeof(siEx);
        siEx.lpAttributeList = attrList;

        DWORD flags = EXTENDED_STARTUPINFO_PRESENT | CREATE_UNICODE_ENVIRONMENT;
        if (!CreateProcessW(NULL, cmdLine, NULL, NULL, FALSE, flags,
                            envBlock, wdStr, &siEx.StartupInfo, &pi)) {
            mark_failed_with_errno(env, "CreateProcessW failed", result);
            break;
        }
        processCreated = TRUE;

        // We do not need the thread handle.
        if (pi.hThread != NULL) { CloseHandle(pi.hThread); pi.hThread = NULL; }

        jlong handles[1];
        handles[0] = (jlong) (intptr_t) pi.hProcess;
        env->SetLongArrayRegion(outHandles, 0, 1, handles);
        ok = TRUE;
    } while (0);

    if (attrList != NULL) {
        DeleteProcThreadAttributeList(attrList);
        free(attrList);
    }
    if (cmdLine != NULL) free(cmdLine);
    if (envBlock != NULL) free(envBlock);
    if (wdStr != NULL) free(wdStr);

    if (!ok) {
        if (processCreated) {
            TerminateProcess(pi.hProcess, 1);
            CloseHandle(pi.hProcess);
        }
        return 0;
    }
    return (jlong) pi.dwProcessId;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_resizePseudoConsole(
        JNIEnv* env, jclass target, jlong hPC, jint cols, jint rows, jobject result) {
    if (!resolve_conpty() || hPC == 0) {
        mark_failed_with_message(env, "ConPTY not available", result);
        return;
    }
    COORD size;
    size.X = (SHORT) cols;
    size.Y = (SHORT) rows;
    HRESULT hr = pResizePseudoConsole((HPCON) (intptr_t) hPC, size);
    if (FAILED(hr)) {
        mark_failed_with_code(env, "ResizePseudoConsole failed", (int) hr, NULL, result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_closePseudoConsole(
        JNIEnv* env, jclass target, jlong hPC, jobject result) {
    if (!resolve_conpty() || hPC == 0) {
        return;
    }
    pClosePseudoConsole((HPCON) (intptr_t) hPC);
}

#else // WINDOWS_MIN

JNIEXPORT jboolean JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_isConPtyAvailable(JNIEnv* env, jclass target) {
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_createPseudoConsole(
        JNIEnv* env, jclass target,
        jint cols, jint rows,
        jlongArray outHandles,
        jobject result) {
    mark_failed_with_message(env, "ConPTY is not available in the minimal Windows variant", result);
}

JNIEXPORT jlong JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_spawnConPtyProcess(
        JNIEnv* env, jclass target,
        jlong hPCArg,
        jobjectArray command,
        jobjectArray environment,
        jstring workingDir,
        jlongArray outHandles,
        jobject result) {
    mark_failed_with_message(env, "ConPTY is not available in the minimal Windows variant", result);
    return 0;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_resizePseudoConsole(
        JNIEnv* env, jclass target, jlong hPC, jint cols, jint rows, jobject result) {
    mark_failed_with_message(env, "ConPTY is not available in the minimal Windows variant", result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_closePseudoConsole(
        JNIEnv* env, jclass target, jlong hPC, jobject result) {
}

#endif // WINDOWS_MIN

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_waitForProcess(
        JNIEnv* env, jclass target, jlong processHandle, jobject result) {
    if (processHandle == 0) {
        mark_failed_with_message(env, "invalid process handle", result);
        return -1;
    }
    HANDLE h = (HANDLE) (intptr_t) processHandle;
    DWORD wait = WaitForSingleObject(h, INFINITE);
    if (wait == WAIT_FAILED) {
        mark_failed_with_errno(env, "WaitForSingleObject failed", result);
        return -1;
    }
    DWORD exitCode = 0;
    if (!GetExitCodeProcess(h, &exitCode)) {
        mark_failed_with_errno(env, "GetExitCodeProcess failed", result);
        return -1;
    }
    return (jint) exitCode;
}

JNIEXPORT jboolean JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_hasProcessExited(
        JNIEnv* env, jclass target, jlong processHandle, jintArray exitCode, jobject result) {
    if (processHandle == 0) {
        mark_failed_with_message(env, "invalid process handle", result);
        return JNI_FALSE;
    }
    HANDLE h = (HANDLE) (intptr_t) processHandle;
    DWORD wait = WaitForSingleObject(h, 0);
    if (wait == WAIT_OBJECT_0) {
        DWORD code = 0;
        if (!GetExitCodeProcess(h, &code)) {
            mark_failed_with_errno(env, "GetExitCodeProcess failed", result);
            return JNI_FALSE;
        }
        jint codeJ = (jint) code;
        env->SetIntArrayRegion(exitCode, 0, 1, &codeJ);
        return JNI_TRUE;
    }
    if (wait == WAIT_TIMEOUT) {
        return JNI_FALSE;
    }
    mark_failed_with_errno(env, "WaitForSingleObject failed", result);
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_destroyProcess(
        JNIEnv* env, jclass target, jlong processHandle, jlong ptyWriteHandle, jint gracePeriodMs, jobject result) {
    if (processHandle == 0) return;
    HANDLE h = (HANDLE) (intptr_t) processHandle;
    if (ptyWriteHandle != 0 && gracePeriodMs > 0) {
        HANDLE w = (HANDLE) (intptr_t) ptyWriteHandle;
        char ctrlC = 0x03;
        DWORD written = 0;
        WriteFile(w, &ctrlC, 1, &written, NULL);
        if (WaitForSingleObject(h, (DWORD) gracePeriodMs) == WAIT_OBJECT_0) {
            return;
        }
    }
    if (!TerminateProcess(h, 1)) {
        if (GetLastError() != ERROR_ACCESS_DENIED) {
            mark_failed_with_errno(env, "TerminateProcess failed", result);
        }
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_cancelSynchronousIo(
        JNIEnv* env, jclass target, jlong threadHandle, jobject result) {
    if (threadHandle == 0) return;
    HANDLE h = (HANDLE) (intptr_t) threadHandle;
    if (!CancelSynchronousIo(h)) {
        DWORD err = GetLastError();
        if (err != ERROR_NOT_FOUND) {
            mark_failed_with_errno(env, "CancelSynchronousIo failed", result);
        }
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_closeHandle(
        JNIEnv* env, jclass target, jlong handle, jobject result) {
    if (handle == 0) return;
    HANDLE h = (HANDLE) (intptr_t) handle;
    if (h == INVALID_HANDLE_VALUE) return;
    if (!CloseHandle(h)) {
        mark_failed_with_errno(env, "CloseHandle failed", result);
    }
}

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_nativeRead(
        JNIEnv* env, jclass target, jlong handle, jbyteArray buf, jint off, jint len, jobject result) {
    if (handle == 0) {
        mark_failed_with_message(env, "invalid handle", result);
        return -1;
    }
    HANDLE h = (HANDLE) (intptr_t) handle;
    jbyte* data = env->GetByteArrayElements(buf, NULL);
    if (data == NULL) {
        mark_failed_with_message(env, "could not access byte array", result);
        return -1;
    }
    DWORD readBytes = 0;
    BOOL ok = ReadFile(h, data + off, (DWORD) len, &readBytes, NULL);
    DWORD err = ok ? 0 : GetLastError();
    env->ReleaseByteArrayElements(buf, data, 0);
    if (!ok) {
        if (err == ERROR_BROKEN_PIPE || err == ERROR_PIPE_NOT_CONNECTED || err == ERROR_HANDLE_EOF) {
            return -1;
        }
        if (err == ERROR_OPERATION_ABORTED) {
            return -1;
        }
        SetLastError(err);
        mark_failed_with_errno(env, "ReadFile failed", result);
        return -1;
    }
    if (readBytes == 0) {
        return -1;
    }
    return (jint) readBytes;
}

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsPtyFunctions_nativeWrite(
        JNIEnv* env, jclass target, jlong handle, jbyteArray buf, jint off, jint len, jobject result) {
    if (handle == 0) {
        mark_failed_with_message(env, "invalid handle", result);
        return -1;
    }
    HANDLE h = (HANDLE) (intptr_t) handle;
    jbyte* data = env->GetByteArrayElements(buf, NULL);
    if (data == NULL) {
        mark_failed_with_message(env, "could not access byte array", result);
        return -1;
    }
    DWORD wrote = 0;
    BOOL ok = WriteFile(h, data + off, (DWORD) len, &wrote, NULL);
    DWORD err = ok ? 0 : GetLastError();
    env->ReleaseByteArrayElements(buf, data, JNI_ABORT);
    if (!ok) {
        SetLastError(err);
        mark_failed_with_errno(env, "WriteFile failed", result);
        return -1;
    }
    return (jint) wrote;
}

#endif
