// Initial version copied from Initial version copied from https://github.com/JetBrains/intellij-community/blob/master/native/WinFsNotifier/fileWatcher3.c
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#ifdef _WIN32

#include "native.h"
#include "generic.h"
#include "win.h"

JavaVM* jvm = NULL;

typedef struct watch_details {
    HANDLE threadHandle;
    HANDLE stopEventHandle;
    char drivePath[4];
    wchar_t *watchedPath;
    jobject watcherCallback;
} watch_details_t;

// TODO Find the right size for this
#define EVENT_BUFFER_SIZE (16*1024)

#define CREATE_SHARE (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE)
#define CREATE_FLAGS (FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OVERLAPPED)
#define EVENT_MASK (FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME | \
                    FILE_NOTIFY_CHANGE_ATTRIBUTES | FILE_NOTIFY_CHANGE_SIZE | FILE_NOTIFY_CHANGE_LAST_WRITE)

void handlePathChanged(watch_details_t *details, FILE_NOTIFY_INFORMATION *info) {
    // const char *event;
    // if (info->Action == FILE_ACTION_ADDED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
    //     event = "CREATE";
    // } else if (info->Action == FILE_ACTION_REMOVED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
    //     event = "DELETE";
    // } else if (info->Action == FILE_ACTION_MODIFIED) {
    //     event = "CHANGE";
    // } else {
    //     return;  // unknown event
    // }

    wchar_t drivePath[4];
    mbstowcs(drivePath, details->drivePath, 3);
    int pathLen = info->FileNameLength / sizeof(wchar_t);
    wchar_t *changedPath = add_prefix(info->FileName, pathLen, drivePath);
    printf("~~~~ Changed: %ls\n", changedPath);

    if (wcsncmp(details->watchedPath, changedPath, wcslen(details->watchedPath)) != 0) {
        printf("~~~~ Ignoring because root is not watched\n");
        return;
    }

    JNIEnv* env;
    int getEnvStat = jvm->GetEnv((void **)&env, JNI_VERSION_1_6);
    if (getEnvStat == JNI_EDETACHED) {
        int attachThreadStat = jvm->AttachCurrentThread((void **) &env, NULL);
        if (attachThreadStat != JNI_OK) {
            printf("~~~~ Problem with AttachCurrentThread: %d\n", attachThreadStat);
            // TODO Error handling
            return;
        }
    } else if (getEnvStat == JNI_EVERSION) {
        printf("~~~~ Problem with GetEnv: %d\n", getEnvStat);
        // TODO Error handling
        return;
    }

    jstring changedPathJava = wchar_to_java(env, changedPath, pathLen + 3, NULL);
    free(changedPath);

    jclass callback_class = env->GetObjectClass(details->watcherCallback);
    jmethodID methodCallback = env->GetMethodID(callback_class, "pathChanged", "(Ljava/lang/String;)V");
    // TODO Do we need to add a global reference to the string here?
    env->CallVoidMethod(details->watcherCallback, methodCallback, changedPathJava);
}

DWORD WINAPI EventProcessingThread(LPVOID data) {
    watch_details_t *details = (watch_details_t*) data;

    printf("~~~~ Starting thread\n");

    OVERLAPPED overlapped;
    memset(&overlapped, 0, sizeof(overlapped));
    overlapped.hEvent = CreateEvent(NULL, FALSE, FALSE, NULL);

    const char *drivePath = details->drivePath;
    HANDLE hDrive = CreateFileA(drivePath, GENERIC_READ, CREATE_SHARE, NULL, OPEN_EXISTING, CREATE_FLAGS, NULL);

    char buffer[EVENT_BUFFER_SIZE];
    HANDLE handles[2] = {details->stopEventHandle, overlapped.hEvent};
    while (true) {
        int rcDrive = ReadDirectoryChangesW(hDrive, buffer, sizeof(buffer), TRUE, EVENT_MASK, NULL, &overlapped, NULL);
        if (rcDrive == 0) {
            // TODO Error handling
            printf("~~~~ Couldn't read directory: %d\n", rcDrive);
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

                // Got a buffer overflow => current changes lost => send RECDIRTY on root
                // TODO Signal overflow
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
    return 0;
}

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsFileEventFunctions_startWatching(JNIEnv *env, jclass target, jobjectArray paths, jobject javaCallback, jobject result) {

    // TODO Should this be somewhere global?
    int jvmStatus = env->GetJavaVM(&jvm);
    if (jvmStatus < 0) {
        mark_failed_with_errno(env, "Could not store jvm instance.", result);
        return NULL;
    }

    jstring path = (jstring) env->GetObjectArrayElement(paths, 0);
    wchar_t* watchedPath = java_to_wchar_path(env, path, result);
    int watchedPathLen = wcslen(watchedPath);
    if (watchedPath[watchedPathLen - 1] != L'\\') {
        printf("~~~~ Appending \\ to watched root path %ls\n", watchedPath);
        wchar_t* oldWatchedPath = watchedPath;
        watchedPath = add_suffix(watchedPath, watchedPathLen, L"\\");
        free(oldWatchedPath);
    }
    printf("~~~~ Watching root %ls\n", watchedPath);
    char drivePath[4] = {toupper(watchedPath[0]), ':', '\\', '\0'};

    watch_details_t* details = (watch_details_t*)malloc(sizeof(watch_details_t));
    details->watcherCallback = env->NewGlobalRef(javaCallback);
    details->stopEventHandle = CreateEvent(NULL, FALSE, FALSE, NULL);
    details->watchedPath = watchedPath;
    strcpy_s(details->drivePath, 4, drivePath);
    details->threadHandle = CreateThread(
        NULL,                   // default security attributes
        0,                      // use default stack size
        EventProcessingThread,  // thread function name
        details,                // argument to thread function
        0,                      // use default creation flags
        NULL                    // the thread identifier
    );
    SetThreadPriority(details->threadHandle, THREAD_PRIORITY_ABOVE_NORMAL);

    jclass clsWatch = env->FindClass("net/rubygrapefruit/platform/internal/jni/WindowsFileEventFunctions$WatchImpl");
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
    free(details->watchedPath);
    env->DeleteGlobalRef(details->watcherCallback);
    free(details);
}

#endif
