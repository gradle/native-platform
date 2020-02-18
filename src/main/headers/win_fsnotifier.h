#ifdef _WIN32

#include "generic.h"
#include "net_rubygrapefruit_platform_internal_jni_WindowsFileEventFunctions.h"
#include "win.h"
#include <list>
#include <mutex>
#include <string>
#include <thread>

using namespace std;

// TODO Find the right size for this
#define EVENT_BUFFER_SIZE (16 * 1024)

#define CREATE_SHARE (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE)
#define CREATE_FLAGS (FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OVERLAPPED)

#define EVENT_MASK (FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME | FILE_NOTIFY_CHANGE_ATTRIBUTES | FILE_NOTIFY_CHANGE_SIZE | FILE_NOTIFY_CHANGE_LAST_WRITE)

class Server;
class WatchPoint;

#define WATCH_LISTENING 1
#define WATCH_NOT_LISTENING 2
#define WATCH_FINISHED 3
#define WATCH_UNINITIALIZED -1
#define WATCH_FAILED_TO_LISTEN -2

class WatchPoint {
public:
    WatchPoint(Server* server, wstring path, HANDLE directoryHandle);
    ~WatchPoint();
    void close();
    void listen();
    int awaitListeningStarted(DWORD dwMilliseconds);

private:
    Server* server;
    wstring path;
    HANDLE directoryHandle;
    HANDLE listeningStartedEvent;
    OVERLAPPED overlapped;
    FILE_NOTIFY_INFORMATION* buffer;
    volatile int status;

    void handleEvent(DWORD errorCode, DWORD bytesTransferred);
    void handlePathChanged(FILE_NOTIFY_INFORMATION* info);
    friend static void CALLBACK handleEventCallback(DWORD errorCode, DWORD bytesTransferred, LPOVERLAPPED overlapped);
};

class Server {
public:
    Server(JavaVM* jvm, JNIEnv* env, jobject watcherCallback);
    ~Server();

    void startWatching(JNIEnv* env, wchar_t* path);
    void reportEvent(jint type, const wstring changedPath);
    void reportFinished(WatchPoint* watchPoint);

    void close(JNIEnv* env);

    // TODO: Move this to somewhere else
    JNIEnv* getThreadEnv();

private:
    JavaVM* jvm;
    list<WatchPoint*> watchPoints;
    jobject watcherCallback;

    thread watcherThread;
    mutex watcherThreadMutex;
    condition_variable watcherThreadStarted;
    bool terminate = false;

    friend static void CALLBACK requestTerminationCallback(_In_ ULONG_PTR arg);
    void requestTermination();

    friend static unsigned CALLBACK EventProcessingThread(void* data);
    void run();
};

#endif
