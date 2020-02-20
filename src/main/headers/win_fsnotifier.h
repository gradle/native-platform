#pragma once

#ifdef _WIN32

#include "generic.h"
#include "generic_fsnotifier.h"
#include "net_rubygrapefruit_platform_internal_jni_WindowsFileEventFunctions.h"
#include "win.h"
#include <list>
#include <string>

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
    WatchPoint(Server* server, const wstring& path, HANDLE directoryHandle);
    ~WatchPoint();
    void close();
    void listen();
    int awaitListeningStarted(HANDLE threadHandle);

private:
    Server* server;
    wstring path;
    HANDLE directoryHandle;
    OVERLAPPED overlapped;
    FILE_NOTIFY_INFORMATION* buffer;

    volatile int status;
    mutex listenerMutex;
    condition_variable listenerStarted;

    void handleEvent(DWORD errorCode, DWORD bytesTransferred);
    void handlePathChanged(FILE_NOTIFY_INFORMATION* info);
    friend static void CALLBACK handleEventCallback(DWORD errorCode, DWORD bytesTransferred, LPOVERLAPPED overlapped);
};

class Server : public AbstractServer {
public:
    Server(JNIEnv* env, jobject watcherCallback);
    ~Server();

    void startWatching(JNIEnv* env, const wstring& path);
    void reportEvent(jint type, const wstring& changedPath);
    void reportFinished(WatchPoint* watchPoint);

    void close(JNIEnv* env);

protected:
    void Server::runLoop(JNIEnv* env, function<void()> notifyStarted) override;

private:
    list<WatchPoint*> watchPoints;

    bool terminate = false;

    friend static void CALLBACK requestTerminationCallback(_In_ ULONG_PTR arg);
    void requestTermination();
};

#endif
