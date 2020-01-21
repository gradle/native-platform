#ifdef _WIN32

#include "native.h"
#include "generic.h"
#include "win.h"
#include <vector>

using namespace std;

// TODO Find the right size for this
#define EVENT_BUFFER_SIZE (16*1024)

#define CREATE_SHARE (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE)
#define CREATE_FLAGS (FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OVERLAPPED)
#define EVENT_MASK (FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME | \
                    FILE_NOTIFY_CHANGE_ATTRIBUTES | FILE_NOTIFY_CHANGE_SIZE | FILE_NOTIFY_CHANGE_LAST_WRITE)

class Server {
public:
    Server(
        JavaVM *jvm,
        JNIEnv *env,
        jobject watcherCallback,
        wchar_t drivePath[4],
        vector<wchar_t*>* watchedPaths
    ) {
        this->jvm = jvm;
        this->watcherCallback = env->NewGlobalRef(watcherCallback);
        this->stopEventHandle = CreateEvent(NULL, FALSE, FALSE, NULL);
        this->watchedPaths = watchedPaths;
        wcscpy_s(this->drivePath, 4, drivePath);

        this->threadHandle = CreateThread(
            NULL,                   // default security attributes
            0,                      // use default stack size
            EventProcessingThread,  // thread function name
            this,                   // argument to thread function
            0,                      // use default creation flags
            NULL                    // the thread identifier
        );
        SetThreadPriority(this->threadHandle, THREAD_PRIORITY_ABOVE_NORMAL);
    }

    void close(JNIEnv *env) {
        SetEvent(this->stopEventHandle);
        WaitForSingleObject(this->threadHandle, INFINITE);
        CloseHandle(this->threadHandle);
        CloseHandle(this->stopEventHandle);
        for (auto watchedPath : *watchedPaths) {
            free(watchedPath);
        }
        delete watchedPaths;
        env->DeleteGlobalRef(this->watcherCallback);
    }

    static DWORD WINAPI EventProcessingThread(LPVOID data) {
        Server *server = (Server*) data;
        server->run();
        return 0;
    }

private:
    JavaVM *jvm;
    wchar_t drivePath[4];
    vector<wchar_t*>* watchedPaths;
    jobject watcherCallback;

    HANDLE stopEventHandle;
    HANDLE threadHandle;

    void run() {
        printf("~~~~ Starting thread\n");

        // TODO Extract this logic to some shared function
        JNIEnv *env;
        jint statAttach = jvm->AttachCurrentThreadAsDaemon((void **) &(env), NULL);
        if (statAttach != JNI_OK) {
            printf("Failed to attach JNI to current thread: %d\n", statAttach);
            return;
        }

        OVERLAPPED overlapped;
        memset(&overlapped, 0, sizeof(overlapped));
        overlapped.hEvent = CreateEvent(NULL, FALSE, FALSE, NULL);

        HANDLE hDrive = CreateFileW(drivePath, GENERIC_READ, CREATE_SHARE, NULL, OPEN_EXISTING, CREATE_FLAGS, NULL);

        char buffer[EVENT_BUFFER_SIZE];
        HANDLE handles[2] = {stopEventHandle, overlapped.hEvent};
        while (true) {
            int rcDrive = ReadDirectoryChangesW(
                hDrive,
                buffer,
                sizeof(buffer),
                TRUE,
                EVENT_MASK,
                NULL,
                &overlapped,
                NULL
            );
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
                    if (WaitForSingleObject(stopEventHandle, 500) == WAIT_OBJECT_0)
                        break;

                    // Got a buffer overflow => current changes lost => send INVALIDATE on root
                    reportEvent(env, FILE_EVENT_INVALIDATE, drivePath, 3);
                } else {
                    FILE_NOTIFY_INFORMATION *info = (FILE_NOTIFY_INFORMATION *)buffer;
                    do {
                        handlePathChanged(env, info);
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
            return;
        }

        return;
    }

    void reportEvent(JNIEnv *env, jint type, wchar_t *changedPath, int changedPathLen) {
        jstring changedPathJava = wchar_to_java(env, changedPath, changedPathLen, NULL);
        jclass callback_class = env->GetObjectClass(watcherCallback);
        jmethodID methodCallback = env->GetMethodID(callback_class, "pathChanged", "(ILjava/lang/String;)V");
        // TODO Do we need to add a global reference to the string here?
        env->CallVoidMethod(watcherCallback, methodCallback, type, changedPathJava);
    }

    void handlePathChanged(JNIEnv *env, FILE_NOTIFY_INFORMATION *info) {
        int pathLen = info->FileNameLength / sizeof(wchar_t);
        wchar_t *changedPath = add_prefix(info->FileName, pathLen, drivePath);
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
        for (auto watchedPath : *watchedPaths) {
            wprintf(L"~~~~ Checking if '%ls' starts with '%ls'\n", changedPath, watchedPath);
            if (wcsncmp(watchedPath, changedPath, wcslen(watchedPath)) == 0) {
                watching = true;
                break;
            }
        }
        if (!watching) {
            wprintf(L"~~~~ Ignoring %ls (root is not watched)\n", changedPath);
            return;
        }

        reportEvent(env, type, changedPath, changedPathLen);
        free(changedPath);
    }
};

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

    vector<wchar_t*>* watchedPaths = new vector<wchar_t*>();

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
        watchedPaths->push_back(watchedPath);
    }
    wchar_t drivePath[4] = {towupper(driveLetter), L':', L'\\', L'\0'};

    Server* server = new Server(
        jvm,
        env,
        javaCallback,
        drivePath,
        watchedPaths
    );

    jclass clsWatch = env->FindClass("net/rubygrapefruit/platform/internal/jni/WindowsFileEventFunctions$WatcherImpl");
    jmethodID constructor = env->GetMethodID(clsWatch, "<init>", "(Ljava/lang/Object;)V");
    return env->NewObject(clsWatch, constructor, env->NewDirectByteBuffer(server, sizeof(server)));
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsFileEventFunctions_stopWatching(JNIEnv *env, jclass target, jobject detailsObj, jobject result) {
    Server* server = (Server*)env->GetDirectBufferAddress(detailsObj);
    server->close(env);
    delete server;
}

#endif
