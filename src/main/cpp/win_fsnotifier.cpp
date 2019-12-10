// Initial version copied from Initial version copied from https://github.com/JetBrains/intellij-community/blob/master/native/WinFsNotifier/fileWatcher3.c
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#ifdef _WIN32

#include "native.h"
#include "generic.h"
#include "win.h"

#include <ctype.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct {
    char rootPath[4];
    HANDLE hThread;
    HANDLE hStopEvent;
    bool bInitialized;
    bool bUsed;
    bool bFailed;
} WatchDrive;

#define ROOT_COUNT ('Z'-'A'+1)
static WatchDrive watchDrive[ROOT_COUNT];

typedef struct __WatchRoot {
    char *path;
    struct __WatchRoot *next;
} WatchRoot;

static WatchRoot *firstWatchRoot = NULL;

static CRITICAL_SECTION csOutput;

#define EVENT_BUFFER_SIZE (16*1024)

// -- Utilities ---------------------------------------------------

#define IS_SET(flags, flag) (((flags) & (flag)) == (flag))
#define FILE_SHARE_ALL (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE)

typedef DWORD (WINAPI *GetFinalPathNameByHandlePtr)(HANDLE, LPCWSTR, DWORD, DWORD);
static GetFinalPathNameByHandlePtr __GetFinalPathNameByHandle = NULL;

typedef struct {
    char *text;
    size_t size;
} PrintBuffer;

static void AppendString(PrintBuffer *buffer, const char *str) {
    if (buffer->text == NULL || strlen(buffer->text) + strlen(str) + 1 > buffer->size) {
        size_t newSize = buffer->size + max(4096, strlen(str));
        char *newData = (char *)malloc(newSize);
        if (buffer->text != NULL) {
            strcpy_s(newData, newSize, buffer->text);
            free(buffer->text);
        } else {
            newData[0] = '\0';
        }
        buffer->text = newData;
        buffer->size = newSize;
    }
    strcat_s(buffer->text, buffer->size, str);
}

// -- Volume operations ---------------------------------------------------

static bool IsDriveWatchable(const char *rootPath) {
    UINT type = GetDriveTypeA(rootPath);
    if (type == DRIVE_REMOVABLE || type == DRIVE_FIXED || type == DRIVE_RAMDISK) {
        char fsName[MAX_PATH + 1];
        if (GetVolumeInformationA(rootPath, NULL, 0, NULL, NULL, NULL, fsName, sizeof(fsName))) {
            return strcmp(fsName, "NTFS") == 0 ||
                   strcmp(fsName, "FAT") == 0 ||
                   strcmp(fsName, "FAT32") == 0 ||
                   _stricmp(fsName, "exFAT") == 0 ||
                   _stricmp(fsName, "reFS") == 0;
        }
    }

    return false;
}

static bool IsPathWatchable(const char *pathToWatch) {
    bool watchable = true;
    int pathLen = MultiByteToWideChar(CP_UTF8, 0, pathToWatch, -1, NULL, 0);
    wchar_t *path = (wchar_t *)calloc((size_t)pathLen + 1, sizeof(wchar_t));
    MultiByteToWideChar(CP_UTF8, 0, pathToWatch, -1, path, pathLen);
    wchar_t buffer[1024];
    const unsigned int bufferSize = 1024;

    wchar_t *pSlash;
    while ((pSlash = wcsrchr(path, L'\\')) != NULL) {
        DWORD attributes = GetFileAttributesW(path);
        if (attributes != INVALID_FILE_ATTRIBUTES && IS_SET(attributes, FILE_ATTRIBUTE_REPARSE_POINT)) {
            if (__GetFinalPathNameByHandle != NULL) {
                HANDLE h = CreateFileW(path, 0, FILE_SHARE_ALL, NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, NULL);
                if (h != NULL) {
                    DWORD result = __GetFinalPathNameByHandle(h, buffer, bufferSize, 0);
                    CloseHandle(h);
                    if (result > 0 && result < bufferSize && wcsncmp(buffer, L"\\\\?\\UNC\\", 8) == 0) {
                        watchable = false;
                        break;
                    }
                }
            }

            path[pathLen] = L'\\';
            path[pathLen + 1] = L'\0';
            if (GetVolumeNameForVolumeMountPointW(path, buffer, bufferSize) != 0) {
                watchable = false;
                break;
            }
        }

        *pSlash = L'\0';
        pathLen = (int)(pSlash - path);
    }

    free(path);
    return watchable;
}

static void PrintUnwatchableDrives(PrintBuffer *buffer, UINT32 unwatchable) {
    for (int i = 0; i < ROOT_COUNT; i++) {
        if (IS_SET(unwatchable, 1 << i)) {
            AppendString(buffer, watchDrive[i].rootPath);
            AppendString(buffer, "\n");
        }
    }
}

static void PrintUnwatchablePaths(PrintBuffer *buffer, UINT32 unwatchable) {
    for (WatchRoot *root = firstWatchRoot; root; root = root->next) {
        const char *path = root->path;
        int drive = toupper(*path);
        if (drive < 'A' || drive > 'Z' ||
            (!IS_SET(unwatchable, 1 << (drive - 'A')) && !IsPathWatchable(path))) {
            AppendString(buffer, path);
            AppendString(buffer, "\n");
        }
    }
}

static void PrintRemapForSubstDrives(PrintBuffer *buffer) {
    wchar_t targetPath[MAX_PATH];
    char targetPathUtf[4 * MAX_PATH];
    for (int i = 0; i < ROOT_COUNT; i++) {
        if (watchDrive[i].bUsed) {
            wchar_t device[3] = {btowc(watchDrive[i].rootPath[0]), L':', L'\0'};
            DWORD res = QueryDosDeviceW(device, targetPath, sizeof(targetPath) / sizeof(wchar_t));
            if (res > 4 && targetPath[0] == L'\\' && targetPath[1] == L'?' && targetPath[2] == L'?' && targetPath[3] == L'\\') {
                WideCharToMultiByte(CP_UTF8, 0, targetPath + 4, -1, targetPathUtf, sizeof(targetPathUtf), NULL, NULL);
                AppendString(buffer, watchDrive[i].rootPath);
                AppendString(buffer, "\n");
                AppendString(buffer, targetPathUtf);
                AppendString(buffer, "\n");
            }
        }
    }
}

// -- Watcher thread ----------------------------------------------------------

static void PrintChangeInfo(const char *rootPath, FILE_NOTIFY_INFORMATION *info) {
    const char *event;
    if (info->Action == FILE_ACTION_ADDED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
        event = "CREATE";
    } else if (info->Action == FILE_ACTION_REMOVED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
        event = "DELETE";
    } else if (info->Action == FILE_ACTION_MODIFIED) {
        event = "CHANGE";
    } else {
        return;  // unknown event
    }

    char utfBuffer[4 * MAX_PATH + 1];
    int wcsLen = info->FileNameLength / sizeof(wchar_t);
    int converted = WideCharToMultiByte(CP_UTF8, 0, info->FileName, wcsLen, utfBuffer, sizeof(utfBuffer), NULL, NULL);
    utfBuffer[converted] = '\0';

    EnterCriticalSection(&csOutput);
    puts(event);
    printf(rootPath);
    puts(utfBuffer);
    fflush(stdout);
    LeaveCriticalSection(&csOutput);
}

static void PrintEverythingChangedUnderRoot(const char *rootPath) {
    EnterCriticalSection(&csOutput);
    puts("RECDIRTY");
    puts(rootPath);
    fflush(stdout);
    LeaveCriticalSection(&csOutput);
}

#define CREATE_SHARE (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE)
#define CREATE_FLAGS (FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OVERLAPPED)
#define EVENT_MASK (FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME | \
                    FILE_NOTIFY_CHANGE_ATTRIBUTES | FILE_NOTIFY_CHANGE_SIZE | FILE_NOTIFY_CHANGE_LAST_WRITE)

static DWORD WINAPI WatcherThread(void *param) {
    WatchDrive *drive = (WatchDrive *)param;

    OVERLAPPED overlapped;
    memset(&overlapped, 0, sizeof(overlapped));
    overlapped.hEvent = CreateEvent(NULL, FALSE, FALSE, NULL);

    const char *rootPath = drive->rootPath;
    HANDLE hRootDir = CreateFileA(rootPath, GENERIC_READ, CREATE_SHARE, NULL, OPEN_EXISTING, CREATE_FLAGS, NULL);

    char buffer[EVENT_BUFFER_SIZE];
    HANDLE handles[2] = {drive->hStopEvent, overlapped.hEvent};
    while (true) {
        int rcDir = ReadDirectoryChangesW(hRootDir, buffer, sizeof(buffer), TRUE, EVENT_MASK, NULL, &overlapped, NULL);
        if (rcDir == 0) {
            drive->bFailed = true;
            break;
        }

        DWORD rc = WaitForMultipleObjects(2, handles, FALSE, INFINITE);
        if (rc == WAIT_OBJECT_0) {
            break;
        }
        if (rc == WAIT_OBJECT_0 + 1) {
            DWORD dwBytesReturned;
            if (!GetOverlappedResult(hRootDir, &overlapped, &dwBytesReturned, FALSE)) {
                drive->bFailed = true;
                break;
            }

            if (dwBytesReturned == 0) {
                // don't send dirty too much, everything is changed anyway
                if (WaitForSingleObject(drive->hStopEvent, 500) == WAIT_OBJECT_0)
                    break;

                // Got a buffer overflow => current changes lost => send RECDIRTY on root
                PrintEverythingChangedUnderRoot(rootPath);
            } else {
                FILE_NOTIFY_INFORMATION *info = (FILE_NOTIFY_INFORMATION *)buffer;
                do {
                    PrintChangeInfo(rootPath, info);
                    info = (FILE_NOTIFY_INFORMATION *)((char *)info + info->NextEntryOffset);
                } while (info->NextEntryOffset != 0);
            }
        }
    }

    CloseHandle(overlapped.hEvent);
    CloseHandle(hRootDir);
    return 0;
}

// -- Roots update ------------------------------------------------------------

static void MarkAllRootsUnused() {
    for (int i = 0; i < ROOT_COUNT; i++) {
        watchDrive[i].bUsed = false;
    }
}

static void StartRoot(WatchDrive *drive) {
    drive->hStopEvent = CreateEvent(NULL, FALSE, FALSE, NULL);
    drive->hThread = CreateThread(NULL, 0, &WatcherThread, drive, 0, NULL);
    SetThreadPriority(drive->hThread, THREAD_PRIORITY_ABOVE_NORMAL);
    drive->bInitialized = true;
}

static void StopRoot(WatchDrive *drive) {
    SetEvent(drive->hStopEvent);
    WaitForSingleObject(drive->hThread, INFINITE);
    CloseHandle(drive->hThread);
    CloseHandle(drive->hStopEvent);
    drive->bInitialized = false;
}

static void UpdateRoots(bool report) {
    UINT32 unwatchable = 0;
    for (int i = 0; i < ROOT_COUNT; i++) {
        if (watchDrive[i].bInitialized && (!watchDrive[i].bUsed || watchDrive[i].bFailed)) {
            StopRoot(&watchDrive[i]);
            watchDrive[i].bFailed = false;
        }
        if (watchDrive[i].bUsed) {
            if (!IsDriveWatchable(watchDrive[i].rootPath)) {
                unwatchable |= (1 << i);
                watchDrive[i].bUsed = false;
                continue;
            }
            if (!watchDrive[i].bInitialized) {
                StartRoot(&watchDrive[i]);
            }
        }
    }

    if (!report) {
        return;
    }

    PrintBuffer buffer = {NULL, 0};
    AppendString(&buffer, "UNWATCHEABLE\n");
    PrintUnwatchableDrives(&buffer, unwatchable);
    PrintUnwatchablePaths(&buffer, unwatchable);
    AppendString(&buffer, "#\nREMAP\n");
    PrintRemapForSubstDrives(&buffer);
    AppendString(&buffer, "#");

    EnterCriticalSection(&csOutput);
    puts(buffer.text);
    fflush(stdout);
    LeaveCriticalSection(&csOutput);

    free(buffer.text);
}

static void AddWatchRoot(const char *path) {
    WatchRoot *root = (WatchRoot *)malloc(sizeof(WatchRoot));
    root->next = NULL;
    root->path = _strdup(path);
    root->next = firstWatchRoot;
    firstWatchRoot = root;
}

static void FreeWatchRootsList() {
    WatchRoot *root = firstWatchRoot, *next;
    while (root) {
        next = root->next;
        free(root->path);
        free(root);
        root = next;
    }
    firstWatchRoot = NULL;
}

// -- Main - file watcher protocol ---------------------------------------------

int main(int argc, char *argv[]) {
    SetErrorMode(SEM_FAILCRITICALERRORS);
    __GetFinalPathNameByHandle = (GetFinalPathNameByHandlePtr)GetProcAddress(GetModuleHandleW(L"kernel32.dll"), "GetFinalPathNameByHandleW");
    InitializeCriticalSection(&csOutput);

    for (int i = 0; i < ROOT_COUNT; i++) {
        char rootPath[4] = {(char)('A' + i), ':', '\\', '\0'};
        strcpy_s(watchDrive[i].rootPath, 4, rootPath);
        watchDrive[i].bInitialized = false;
        watchDrive[i].bUsed = false;
    }

    char buffer[8192];
    while (true) {
        if (!gets_s(buffer, sizeof(buffer) - 1) || strcmp(buffer, "EXIT") == 0) {
            break;
        }

        if (strcmp(buffer, "ROOTS") == 0) {
            MarkAllRootsUnused();
            FreeWatchRootsList();

            bool failed = false;
            while (true) {
                if (!gets_s(buffer, sizeof(buffer) - 1)) {
                    failed = true;
                    break;
                }
                if (strlen(buffer) == 0) {
                    continue;
                }
                if (buffer[0] == '#') {
                    break;
                }

                char *root = buffer;
                if (*root == '|') root++;
                AddWatchRoot(root);
                int driveLetter = toupper(*root);
                if (driveLetter >= 'A' && driveLetter <= 'Z') {
                    watchDrive[driveLetter - 'A'].bUsed = true;
                }
            }
            if (failed) {
                break;
            }

            UpdateRoots(true);
        }
    }

    MarkAllRootsUnused();
    UpdateRoots(false);
    DeleteCriticalSection(&csOutput);

    return 0;
}

// File watching

JavaVM* jvm = NULL;

typedef struct watch_details {
    HANDLE watchHandle;
    HANDLE threadHandle;
    jstring path;
    jobject watcherCallback;
} watch_details_t;

DWORD WINAPI EventProcessingThread(LPVOID data) {
    watch_details_t *details = (watch_details_t*) data;
    while (TRUE) {
        if (WaitForSingleObject(details->watchHandle, INFINITE) == WAIT_FAILED) {
            // TODO Error handling
            // mark_failed_with_errno(env, "could not wait for change notification", result);
            break;
        }
        if (!FindNextChangeNotification(details->watchHandle)) {
            if (GetLastError() == ERROR_INVALID_HANDLE) {
                // Assumed closed
                break;
            }
            // TODO Error handling
            // mark_failed_with_errno(env, "could not schedule next change notification", result);
            break;
        }

        JNIEnv* env;
        int getEnvStat = jvm->GetEnv((void **)&env, JNI_VERSION_1_6);
        if (getEnvStat == JNI_EDETACHED) {
            int attachThreadStat = jvm->AttachCurrentThread((void **) &env, NULL);
            if (attachThreadStat != JNI_OK) {
                printf("~~~~ Problem with AttachCurrentThread: %d\n", attachThreadStat);
                // TODO Error handling
                // invalidStateDetected = true;
                break;
            }
        } else if (getEnvStat == JNI_EVERSION) {
            printf("~~~~ Problem with GetEnv: %d\n", getEnvStat);
            // TODO Error handling
            // invalidStateDetected = true;
            break;
        }

        wchar_t* pathStr = java_to_wchar_path(env, details->path, NULL);
        printf("~~~~ Changes: %ls\n", pathStr);
        free(pathStr);

        jclass callback_class = env->GetObjectClass(details->watcherCallback);
        jmethodID methodCallback = env->GetMethodID(callback_class, "pathChanged", "(Ljava/lang/String;)V");
        env->CallVoidMethod(details->watcherCallback, methodCallback, details->path);
    }
    return 0;
}

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsFileEventFunctions_startWatch(JNIEnv *env, jclass target, jstring path, jobject javaCallback, jobject result) {
    wchar_t* pathStr = java_to_wchar_path(env, path, result);
    HANDLE watchHandle = FindFirstChangeNotificationW(
        pathStr,
        TRUE,
        FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME | FILE_NOTIFY_CHANGE_ATTRIBUTES | FILE_NOTIFY_CHANGE_SIZE | FILE_NOTIFY_CHANGE_LAST_WRITE
    );
    free(pathStr);
    if (watchHandle == INVALID_HANDLE_VALUE) {
        mark_failed_with_errno(env, "could not open change notification", result);
        return NULL;
    }

    // TODO Should this be somewhere global?
    int jvmStatus = env->GetJavaVM(&jvm);
    if (jvmStatus < 0) {
        mark_failed_with_errno(env, "Could not store jvm instance.", result);
        return NULL;
    }

    watch_details_t* details = (watch_details_t*)malloc(sizeof(watch_details_t));
    details->watchHandle = watchHandle;
    details->path = (jstring) env->NewGlobalRef(path);
    details->watcherCallback = env->NewGlobalRef(javaCallback);

    details->threadHandle = CreateThread(
        NULL,                   // default security attributes
        0,                      // use default stack size
        EventProcessingThread,  // thread function name
        details,                // argument to thread function
        0,                      // use default creation flags
        NULL                    // the thread identifier
    );

    jclass clsWatch = env->FindClass("net/rubygrapefruit/platform/internal/jni/WindowsFileEventFunctions$WatchImpl");
    jmethodID constructor = env->GetMethodID(clsWatch, "<init>", "(Ljava/lang/Object;)V");
    return env->NewObject(clsWatch, constructor, env->NewDirectByteBuffer(details, sizeof(details)));
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsFileEventFunctions_stopWatch(JNIEnv *env, jclass target, jobject detailsObj, jobject result) {
    watch_details_t* details = (watch_details_t*)env->GetDirectBufferAddress(detailsObj);
    env->DeleteGlobalRef(details->path);
    env->DeleteGlobalRef(details->watcherCallback);
    FindCloseChangeNotification(details->watchHandle);
    CloseHandle(details->threadHandle);
    free(details);
}

#endif
