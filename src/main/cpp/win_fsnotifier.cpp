#ifdef _WIN32

#include "native.h"
#include "generic.h"
#include "win.h"
#include <process.h>
#include <list>
#include <vector>
#include <string>

using namespace std;

// TODO Find the right size for this
#define EVENT_BUFFER_SIZE (16*1024)

#define CREATE_SHARE (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE)
#define CREATE_FLAGS (FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OVERLAPPED)
#define EVENT_MASK (FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME | \
                    FILE_NOTIFY_CHANGE_ATTRIBUTES | FILE_NOTIFY_CHANGE_SIZE | FILE_NOTIFY_CHANGE_LAST_WRITE)

jstring wstring_to_java(JNIEnv* env, const wstring &string) {
    return env->NewString((jchar*) (string.c_str()), string.length());
}

class WatchPoint {
public:
    WatchPoint(Server *server, wchar_t *path) {
        this->server = server;
        this->path = path;
        this->buffer.resize(EVENT_BUFFER_SIZE);
        ZeroMemory(&this->overlapped, sizeof(OVERLAPPED));
        this->overlapped.hEvent = this;
        this->directoryHandle = CreateFileW(
            path,				                // pointer to the file name
            GENERIC_READ,                       // access (read/write) mode
            CREATE_SHARE,                       // share mode
            NULL,                               // security descriptor
            OPEN_EXISTING,                      // how to create
            CREATE_FLAGS,                       // file attributes
            NULL                                // file with attributes to copy
        );

        // TODO Error handling
    }

    void close() {
        CancelIo(directoryHandle);
        CloseHandle(directoryHandle);
    }

    void listen() {
        BOOL success = ReadDirectoryChangesW(
            directoryHandle,			    	// handle to directory
            &buffer[0],                         // read results buffer
            buffer.size(),                      // length of buffer
            TRUE,                               // monitoring option
            EVENT_MASK,                         // filter conditions
            NULL,                               // bytes returned
            &overlapped,                        // overlapped buffer
            &handleEvent                        // completion routine
        );
    }

private:
    Server *server;
    wstring path;
    HANDLE directoryHandle;
    OVERLAPPED overlapped;
    vector<BYTE> buffer;

    static void __stdcall handleEvent(DWORD errorCode, DWORD bytesTransfered, LPOVERLAPPED overlapped) {
        WatchPoint* watchPoint = (WatchPoint*)overlapped->hEvent;

        if (errorCode == ERROR_OPERATION_ABORTED){
            watchPoint->server->reportFinished(*watchPoint);
            delete watchPoint;
            return;
        }

        if (bytesTransfered == 0) {
            // don't send dirty too much, everything is changed anyway
            // TODO Understand what this does
            // if (WaitForSingleObject(stopEventHandle, 500) == WAIT_OBJECT_0)
            //    break;

            // Got a buffer overflow => current changes lost => send INVALIDATE on root
            watchPoint->server->reportEvent(FILE_EVENT_INVALIDATE, watchPoint->path);
        } else {
            watchPoint->handlePathChanged();
        }

        // Get the new read issued as fast as possible. The documentation
        // says that the original OVERLAPPED structure will not be used
        // again once the completion routine is called.
        watchPoint->listen();
    }

    void handlePathChanged() {
        FILE_NOTIFY_INFORMATION *info = (FILE_NOTIFY_INFORMATION *)buffer.data();
        do {
            info = (FILE_NOTIFY_INFORMATION *)((char *)info + info->NextEntryOffset);
        } while (info->NextEntryOffset != 0);
    }

    void handlePathChanged(FILE_NOTIFY_INFORMATION *info) {
        wstring changedPath = wstring(info->FileName, 0, info->FileNameLength / sizeof(wchar_t));
        changedPath.insert(0, path);

        wprintf(L"~~~~ Changed: 0x%x %ls\n", info->Action, changedPath.c_str());

        jint type;
        if (info->Action == FILE_ACTION_ADDED || info->Action == FILE_ACTION_RENAMED_NEW_NAME) {
            type = FILE_EVENT_CREATED;
        } else if (info->Action == FILE_ACTION_REMOVED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
            type = FILE_EVENT_REMOVED;
        } else if (info->Action == FILE_ACTION_MODIFIED) {
            type = FILE_EVENT_MODIFIED;
        } else {
            wprintf(L"~~~~ Unknown event 0x%x for %ls\n", info->Action, changedPath.c_str());
            type = FILE_EVENT_UNKNOWN;
        }

        server->reportEvent(type, changedPath);
    }
};

class Server {
public:
    Server(
        JavaVM *jvm,
        JNIEnv *env,
        jobject watcherCallback
    ) {
        this->jvm = jvm;
        this->watcherCallback = env->NewGlobalRef(watcherCallback);
        this->stopEventHandle = CreateEvent(NULL, FALSE, FALSE, NULL);
        this->threadHandle = (HANDLE)_beginthreadex(
            NULL,                   // default security attributes
            0,                      // use default stack size
            EventProcessingThread,  // thread function name
            this,                   // argument to thread function
            0,                      // use default creation flags
            NULL                    // the thread identifier
        );
        SetThreadPriority(this->threadHandle, THREAD_PRIORITY_ABOVE_NORMAL);
    }

    void startWatching(wchar_t *path) {
        StartWatchRequest request = { this, path };
        QueueUserAPC(&startWatchCallback, threadHandle, ULONG_PTR (&request));
    }

    struct StartWatchRequest {
        Server *server;
        wstring path;
    };

	// Called by QueueUserAPC to add another directory.
	static void __stdcall startWatchCallback(_In_ ULONG_PTR arg) {
		StartWatchRequest *request = (StartWatchRequest*)arg;
        WatchPoint watchPoint = request->server->watchPoints.emplace_back(request->server, request->path);
        watchPoint.listen();
	}

    void reportFinished(const WatchPoint& watchPoint) {
        watchPoints.remove(watchPoint);
    }

    void reportEvent(jint type, const wstring changedPath) {
        JNIEnv* env = getThreadEnv();
        jstring changedPathJava = wstring_to_java(env, changedPath);
        jclass callback_class = env->GetObjectClass(watcherCallback);
        jmethodID methodCallback = env->GetMethodID(callback_class, "pathChanged", "(ILjava/lang/String;)V");
        env->CallVoidMethod(watcherCallback, methodCallback, type, changedPathJava);
    }

    JNIEnv* getThreadEnv() {
        JNIEnv* env;
        jint ret = jvm->GetEnv((void **) &(env), NULL);
        if (ret != JNI_OK) {
            printf("Failed to attach JNI to current thread: %d\n", ret);
            return NULL;
        }
        return env;
    }

    void close(JNIEnv *env) {
        for (auto &watchPoint : watchPoints) {
            watchPoint.close();
        }
        SetEvent(this->stopEventHandle);
        WaitForSingleObject(this->threadHandle, INFINITE);
        CloseHandle(this->threadHandle);
        CloseHandle(this->stopEventHandle);
        env->DeleteGlobalRef(this->watcherCallback);
    }

private:
    JavaVM *jvm;
    list<WatchPoint> watchPoints;
    jobject watcherCallback;

    HANDLE stopEventHandle;
    HANDLE threadHandle;
    bool terminate = false;

    static unsigned CALLBACK EventProcessingThread(void* data) {
        Server *server = (Server*) data;
        server->run();
        return 0;
    }

    void run() {
        printf("~~~~ Starting thread\n");

        // TODO Extract this logic to some shared function
        JNIEnv* env;
        jint statAttach = jvm->AttachCurrentThreadAsDaemon((void **) &(env), NULL);
        if (statAttach != JNI_OK) {
            printf("Failed to attach JNI to current thread: %d\n", statAttach);
            return;
        }

        while (!terminate || watchPoints.size() > 0) {
            SleepEx(INFINITE, true);
        }

        printf("~~~~ Stopping thread\n");

        // TODO Extract this logic to some shared function
        jint statDetach = jvm->DetachCurrentThread();
        if (statDetach != JNI_OK) {
            printf("Failed to detach JNI from current thread: %d\n", statAttach);
            return;
        }
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

    int watchPointCount = env->GetArrayLength(paths);
    if (watchPointCount == 0) {
        mark_failed_with_errno(env, "No paths given to watch.", result);
        return NULL;
    }

    Server* server = new Server(jvm, env, javaCallback);

    for (int i = 0; i < watchPointCount; i++) {
        jstring path = (jstring) env->GetObjectArrayElement(paths, i);
        wchar_t* watchPoint = java_to_wchar_path(env, path, result);
        int watchPointLen = wcslen(watchPoint);
        if (watchPointLen > 240 || watchPoint[0] == L'\\') {
            mark_failed_with_errno(env, "Cannot watch long paths for now.", result);
            return NULL;
        }
        wprintf(L"~~~~ Watching %ls\n", watchPoint);
        server->startWatching(watchPoint);
        free(watchPoint);
    }

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
