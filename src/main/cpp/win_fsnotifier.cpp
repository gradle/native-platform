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

class Server;
class WatchPoint;

class WatchPoint {
public:
    WatchPoint(Server *server, wstring path) {
        this->server = server;
        this->path = path;
        this->buffer.resize(EVENT_BUFFER_SIZE);
        ZeroMemory(&this->overlapped, sizeof(OVERLAPPED));
        this->overlapped.hEvent = this;
        this->directoryHandle = CreateFileW(
            path.c_str(),                       // pointer to the file name
            FILE_LIST_DIRECTORY,                // access (read/write) mode
            CREATE_SHARE,                       // share mode
            NULL,                               // security descriptor
            OPEN_EXISTING,                      // how to create
            CREATE_FLAGS,                       // file attributes
            NULL                                // file with attributes to copy
        );

        // TODO Error handling
    }

    void close();
    void listen();

private:
    Server *server;
    friend static void CALLBACK startWatchCallback(_In_ ULONG_PTR arg);
    wstring path;
    HANDLE directoryHandle;
    OVERLAPPED overlapped;
    vector<BYTE> buffer;

    void handleEvent(DWORD errorCode, DWORD bytesTransfered);
    void handlePathChanged(FILE_NOTIFY_INFORMATION *info);
    friend static void CALLBACK handleEventCallback(DWORD errorCode, DWORD bytesTransfered, LPOVERLAPPED overlapped);
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
        this->threadHandle = (HANDLE)_beginthreadex(
            NULL,                   // default security attributes
            0,                      // use default stack size
            EventProcessingThread,  // thread function name
            this,                   // argument to thread function
            0,                      // use default creation flags
            NULL                    // the thread identifier
        );
        // TODO Error handling
        SetThreadPriority(this->threadHandle, THREAD_PRIORITY_ABOVE_NORMAL);
    }

    void startWatching(wchar_t *path);
    void reportEvent(jint type, const wstring changedPath);
    void reportFinished(WatchPoint* watchPoint);

    void close(JNIEnv *env);

private:
    struct StartWatchRequest {
        Server *server;
        wstring path;
    };
    friend static void CALLBACK startWatchCallback(_In_ ULONG_PTR arg);

    JavaVM *jvm;
    list<WatchPoint*> watchPoints;
    jobject watcherCallback;

    HANDLE threadHandle;
    bool terminate = false;

    friend static void CALLBACK requestTerminationCallback(_In_ ULONG_PTR arg);
    void requestTermination();

    friend static unsigned CALLBACK EventProcessingThread(void* data);
    void run();
};

void WatchPoint::close() {
    CancelIo(directoryHandle);
    CloseHandle(directoryHandle);
}

void WatchPoint::listen() {
    BOOL success = ReadDirectoryChangesW(
        directoryHandle,                    // handle to directory
        &buffer[0],                         // read results buffer
        buffer.size(),                      // length of buffer
        TRUE,                               // include children
        EVENT_MASK,                         // filter conditions
        NULL,                               // bytes returned
        &overlapped,                        // overlapped buffer
        &handleEventCallback                // completion routine
    );
    // TODO Error handling
}

static void CALLBACK handleEventCallback(DWORD errorCode, DWORD bytesTransfered, LPOVERLAPPED overlapped) {
    WatchPoint* watchPoint = (WatchPoint*)overlapped->hEvent;
    watchPoint->handleEvent(errorCode, bytesTransfered);
}

void WatchPoint::handleEvent(DWORD errorCode, DWORD bytesTransfered) {
    if (errorCode == ERROR_OPERATION_ABORTED){
        server->reportFinished(this);
        delete this;
        return;
    }

    if (bytesTransfered == 0) {
        // don't send dirty too much, everything is changed anyway
        // TODO Understand what this does
        // if (WaitForSingleObject(stopEventHandle, 500) == WAIT_OBJECT_0)
        //    break;

        // Got a buffer overflow => current changes lost => send INVALIDATE on root
        server->reportEvent(FILE_EVENT_INVALIDATE, path);
    } else {
        FILE_NOTIFY_INFORMATION *info = (FILE_NOTIFY_INFORMATION *)buffer.data();
        do {
            handlePathChanged(info);
            info = (FILE_NOTIFY_INFORMATION *)((char *)info + info->NextEntryOffset);
        } while (info->NextEntryOffset != 0);
    }

    // Get the new read issued as fast as possible. The documentation
    // says that the original OVERLAPPED structure will not be used
    // again once the completion routine is called.
    listen();
}

void WatchPoint::handlePathChanged(FILE_NOTIFY_INFORMATION *info) {
    wstring changedPath = wstring(info->FileName, 0, info->FileNameLength / sizeof(wchar_t));
    if (!changedPath.empty()) {
        changedPath.insert(0, 1, L'\\');
        changedPath.insert(0, path);
    }

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

void Server::startWatching(wchar_t *path) {
    WatchPoint* watchPoint = new WatchPoint(this, wstring(path));
    QueueUserAPC(&startWatchCallback, threadHandle, (ULONG_PTR) watchPoint);
}

// Called by QueueUserAPC to add another directory.
static void CALLBACK startWatchCallback(_In_ ULONG_PTR arg) {
    WatchPoint* watchPoint = (WatchPoint*)arg;
    watchPoint->server->watchPoints.push_back(watchPoint);
    watchPoint->listen();
}

void Server::reportFinished(WatchPoint* watchPoint) {
    watchPoints.remove(watchPoint);
}

static JNIEnv* getThreadEnv(JavaVM *jvm) {
    JNIEnv* env;
    // TODO Verify that JNI 1.6 is the right version
    jint ret = jvm->GetEnv((void **) &(env), JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        printf("Failed to attach JNI to current thread: %d\n", ret);
        return NULL;
    }
    return env;
}

void Server::reportEvent(jint type, const wstring changedPath) {
    JNIEnv* env = getThreadEnv(jvm);
    jstring changedPathJava = wchar_to_java_path(env, changedPath.c_str());
    jclass callback_class = env->GetObjectClass(watcherCallback);
    jmethodID methodCallback = env->GetMethodID(callback_class, "pathChanged", "(ILjava/lang/String;)V");
    env->CallVoidMethod(watcherCallback, methodCallback, type, changedPathJava);
}

static unsigned CALLBACK EventProcessingThread(void* data) {
    Server *server = (Server*) data;
    server->run();
    return 0;
}

void Server::run() {
    printf("~~~~ Thread %d running\n", GetCurrentThreadId());

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

static void CALLBACK requestTerminationCallback(_In_ ULONG_PTR arg) {
    Server* server = (Server*)arg;
    server->requestTermination();
}

void Server::requestTermination() {
    terminate = true;
    for (auto &watchPoint : watchPoints) {
        watchPoint->close();
    }
}

void Server::close(JNIEnv *env) {
    QueueUserAPC(requestTerminationCallback, this->threadHandle, (ULONG_PTR)this);
    WaitForSingleObject(this->threadHandle, INFINITE);
    CloseHandle(this->threadHandle);
    env->DeleteGlobalRef(this->watcherCallback);
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

    int watchPointCount = env->GetArrayLength(paths);
    if (watchPointCount == 0) {
        mark_failed_with_errno(env, "No paths given to watch.", result);
        return NULL;
    }

    Server* server = new Server(jvm, env, javaCallback);

    for (int i = 0; i < watchPointCount; i++) {
        jstring path = (jstring) env->GetObjectArrayElement(paths, i);
        wchar_t* watchPoint = java_to_wchar_path(env, path);
        int watchPointLen = wcslen(watchPoint);
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
