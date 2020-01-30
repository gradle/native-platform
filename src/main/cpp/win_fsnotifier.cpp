// Initial version copied from Initial version copied from https://github.com/JetBrains/intellij-community/blob/master/native/WinFsNotifier/fileWatcher3.c
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#ifdef _WIN32

#include "net_rubygrapefruit_platform_internal_jni_WindowsFileEventFunctions.h"
#include "generic.h"
#include "win.h"

typedef struct watch_details {
    HANDLE threadHandle;
    HANDLE stopEventHandle;
    JavaVM *jvm;
    JNIEnv *env;
    wchar_t drivePath[4];
    int watchedPathCount;
    wchar_t **watchedPaths;
    jobject watcherCallback;
} watch_details_t;

// TODO Find the right size for this
#define EVENT_BUFFER_SIZE (16*1024)

#define CREATE_SHARE (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE)
#define CREATE_FLAGS (FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OVERLAPPED)
#define EVENT_MASK (FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME | \
                    FILE_NOTIFY_CHANGE_ATTRIBUTES | FILE_NOTIFY_CHANGE_SIZE | FILE_NOTIFY_CHANGE_LAST_WRITE)

void reportEvent(jint type, wchar_t *changedPath, int changedPathLen, watch_details_t *details) {
    JNIEnv *env = details->env;
    jobject watcherCallback = details->watcherCallback;
    jstring changedPathJava = wchar_to_java(env, changedPath, changedPathLen, NULL);
    jclass callback_class = env->GetObjectClass(watcherCallback);
    jmethodID methodCallback = env->GetMethodID(callback_class, "pathChanged", "(ILjava/lang/String;)V");
    // TODO Do we need to add a global reference to the string here?
    env->CallVoidMethod(watcherCallback, methodCallback, type, changedPathJava);
}

void handlePathChanged(watch_details_t *details, FILE_NOTIFY_INFORMATION *info) {
    int pathLen = info->FileNameLength / sizeof(wchar_t);
    wchar_t *changedPath = add_prefix(info->FileName, pathLen, details->drivePath);
    int changedPathLen = pathLen + 3;

    wprintf(L"~~~~ Changed: 0x%x %ls\n", info->Action, changedPath);

    jint type;
    if (info->Action == FILE_ACTION_ADDED || info->Action == FILE_ACTION_RENAMED_NEW_NAME) {
        type = FILE_EVENT_CREATED;
    } else if (info->Action == FILE_ACTION_REMOVED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
        type = FILE_EVENT_REMOVED;
    } else if (info->Action == FILE_ACTION_MODIFIED) {
        type = FILE_EVENT_MODIFIED;
    } else {
        wprintf(L"~~~~ Unknown event 0x%x for %ls\n", info->Action, changedPath);
        type = FILE_EVENT_UNKNOWN;
    }

    bool watching = false;
    for (int i = 0; i < details->watchedPathCount; i++) {
        wchar_t* watchedPath = details->watchedPaths[i];
        if (wcsncmp(watchedPath, changedPath, wcslen(watchedPath)) == 0) {
            watching = true;
            break;
        }
    }
    if (!watching) {
        wprintf(L"~~~~ Ignoring %ls (root is not watched)\n", changedPath);
        return;
    }

    reportEvent(type, changedPath, changedPathLen, details);
    free(changedPath);
}

DWORD WINAPI EventProcessingThread(LPVOID data) {
    watch_details_t *details = (watch_details_t*) data;

    printf("~~~~ Starting thread\n");

    // TODO Extract this logic to some shared function
    JavaVM* jvm = details->jvm;
    jint statAttach = jvm->AttachCurrentThreadAsDaemon((void **) &(details->env), NULL);
    if (statAttach != JNI_OK) {
        printf("Failed to attach JNI to current thread: %d\n", statAttach);
        return 0;
    }

    OVERLAPPED overlapped;
    memset(&overlapped, 0, sizeof(overlapped));
    overlapped.hEvent = CreateEvent(NULL, FALSE, FALSE, NULL);

    HANDLE hDrive = CreateFileW(details->drivePath, GENERIC_READ, CREATE_SHARE, NULL, OPEN_EXISTING, CREATE_FLAGS, NULL);

    char buffer[EVENT_BUFFER_SIZE];
    HANDLE handles[2] = {details->stopEventHandle, overlapped.hEvent};
    while (true) {
        int rcDrive = ReadDirectoryChangesW(hDrive, buffer, sizeof(buffer), TRUE, EVENT_MASK, NULL, &overlapped, NULL);
        if (rcDrive == 0) {
            // TODO Error handling
            printf("~~~~ Couldn't read directory: %d - %d\n", rcDrive, GetLastError());
            break;
        }

        DWORD rc = WaitForMultipleObjects(2, handles, FALSE, INFINITE);
        if (rc == WAIT_OBJECT_0) {
            break;
        }
        if (rc == WAIT_OBJECT_0 + 1) {
            DWORD dwBytesReturned;
            if (!GetOverlappedResult(hDrive, &overlapped, &dwBytesReturned, FALSE)) {
                // TODO Error handling
                printf("~~~~ Failed to wait\n");
                break;
            }

            if (dwBytesReturned == 0) {
                // don't send dirty too much, everything is changed anyway
                // TODO Understand what this does
                if (WaitForSingleObject(details->stopEventHandle, 500) == WAIT_OBJECT_0)
                    break;

                // Got a buffer overflow => current changes lost => send INVALIDATE on root
                reportEvent(FILE_EVENT_INVALIDATE, details->drivePath, 3, details);
            } else {
                FILE_NOTIFY_INFORMATION *info = (FILE_NOTIFY_INFORMATION *)buffer;
                do {
                    handlePathChanged(details, info);
                    info = (FILE_NOTIFY_INFORMATION *)((char *)info + info->NextEntryOffset);
                } while (info->NextEntryOffset != 0);
            }
        }
    }

    printf("~~~~ Stopping thread\n");

    CloseHandle(overlapped.hEvent);
    CloseHandle(hDrive);

    // TODO Extract this logic to some shared function
    jint statDetach = jvm->DetachCurrentThread();
    if (statDetach != JNI_OK) {
        printf("Failed to detach JNI from current thread: %d\n", statAttach);
        return 0;
    }

    return 0;
}

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsFileEventFunctions_startWatching(JNIEnv *env, jclass target, jobjectArray paths, jobject javaCallback, jobject result) {

    printf("\n~~~~ Configuring...\n");

    JavaVM* jvm;
    int jvmStatus = env->GetJavaVM(&jvm);
    if (jvmStatus != JNI_OK) {
        mark_failed_with_errno(env, "Could not store jvm instance.", result);
        return NULL;
    }

    int watchedPathCount = env->GetArrayLength(paths);
    if (watchedPathCount == 0) {
        mark_failed_with_errno(env, "No paths given to watch.", result);
        return NULL;
    }

    wchar_t **watchedPaths = (wchar_t**)malloc(watchedPathCount * sizeof(wchar_t*));
    wchar_t driveLetter = L'\0';
    for (int i = 0; i < watchedPathCount; i++) {
        jstring path = (jstring) env->GetObjectArrayElement(paths, i);
        wchar_t* watchedPath = java_to_wchar_path(env, path, result);
        int watchedPathLen = wcslen(watchedPath);
        if (watchedPathLen > 240 || watchedPath[0] == L'\\') {
            mark_failed_with_errno(env, "Cannot watch long paths for now.", result);
            return NULL;
        }
        if (driveLetter == L'\0') {
            driveLetter = watchedPath[0];
        } else if (driveLetter != watchedPath[0]) {
            mark_failed_with_errno(env, "Cannot watch multiple drives for now.", result);
            return NULL;
        }
        if (watchedPath[watchedPathLen - 1] != L'\\') {
            wchar_t* oldWatchedPath = watchedPath;
            watchedPath = add_suffix(watchedPath, watchedPathLen, L"\\");
            free(oldWatchedPath);
        }
        wprintf(L"~~~~ Watching %ls\n", watchedPath);
        watchedPaths[i] = watchedPath;
    }
    wchar_t drivePath[4] = {towupper(watchedPaths[0][0]), L':', L'\\', L'\0'};

    watch_details_t* details = (watch_details_t*)malloc(sizeof(watch_details_t));
    details->watcherCallback = env->NewGlobalRef(javaCallback);
    details->stopEventHandle = CreateEvent(NULL, FALSE, FALSE, NULL);
    details->jvm = jvm;
    details->watchedPathCount = watchedPathCount;
    details->watchedPaths = watchedPaths;
    wcscpy_s(details->drivePath, 4, drivePath);
    details->threadHandle = CreateThread(
        NULL,                   // default security attributes
        0,                      // use default stack size
        EventProcessingThread,  // thread function name
        details,                // argument to thread function
        0,                      // use default creation flags
        NULL                    // the thread identifier
    );
    SetThreadPriority(details->threadHandle, THREAD_PRIORITY_ABOVE_NORMAL);

    jclass clsWatch = env->FindClass("net/rubygrapefruit/platform/internal/jni/WindowsFileEventFunctions$WatcherImpl");
    jmethodID constructor = env->GetMethodID(clsWatch, "<init>", "(Ljava/lang/Object;)V");
    return env->NewObject(clsWatch, constructor, env->NewDirectByteBuffer(details, sizeof(details)));
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsFileEventFunctions_stopWatching(JNIEnv *env, jclass target, jobject detailsObj, jobject result) {
    watch_details_t* details = (watch_details_t*)env->GetDirectBufferAddress(detailsObj);
    SetEvent(details->stopEventHandle);
    WaitForSingleObject(details->threadHandle, INFINITE);
    CloseHandle(details->threadHandle);
    CloseHandle(details->stopEventHandle);
    for (int i = 0; i < details->watchedPathCount; i++) {
        free(details->watchedPaths[i]);
    }
    free(details->watchedPaths);
    env->DeleteGlobalRef(details->watcherCallback);
    free(details);
}

#endif
