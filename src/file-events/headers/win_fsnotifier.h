#pragma once

#ifdef _WIN32

#include <Shlwapi.h>
#include <functional>
#include <string>
#include <unordered_map>
#include <vector>
#include <wchar.h>
#include <windows.h>

// Needs to stay below <windows.h> otherwise byte symbol gets confused with std::byte
#include "generic_fsnotifier.h"
#include "net_rubygrapefruit_platform_internal_jni_WindowsFileEventFunctions.h"

using namespace std;

#define CREATE_SHARE (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE)
#define CREATE_FLAGS (FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OVERLAPPED)

#define EVENT_MASK (FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME | FILE_NOTIFY_CHANGE_ATTRIBUTES | FILE_NOTIFY_CHANGE_SIZE | FILE_NOTIFY_CHANGE_LAST_WRITE)

class Server;
class WatchPoint;

enum class ListenResult {
    /**
     * Listening succeeded.
     */
    SUCCESS,
    /**
     * Target directory has been removed.
     */
    DELETED
};

enum class WatchPointStatus {
    /**
     * The watch point has been constructed, but not currently listening.
     */
    NOT_LISTENING,

    /**
     * The watch point is listening, expect events to arrive.
     */
    LISTENING,

    /**
     * The watch point has been cancelled, expect ERROR_OPERATION_ABORTED event.
     */
    CANCELLED,

    /**
     * The watch point has been cancelled, the ERROR_OPERATION_ABORTED event arrived; or starting the listener caused an error.
     */
    FINISHED
};

class WatchPoint {
public:
    WatchPoint(Server* server, size_t bufferSize, const u16string& path);
    ~WatchPoint();

    ListenResult listen();
    bool cancel();

private:
    bool isValidDirectory();
    void close();

    Server* server;
    const u16string path;
    friend class Server;
    HANDLE directoryHandle;
    OVERLAPPED overlapped;
    vector<BYTE> buffer;
    WatchPointStatus status;

    void handleEventsInBuffer(DWORD errorCode, DWORD bytesTransferred);
    friend static void CALLBACK handleEventCallback(DWORD errorCode, DWORD bytesTransferred, LPOVERLAPPED overlapped);
};

class Server : public AbstractServer {
public:
    Server(JNIEnv* env, size_t bufferSize, long commandTimeoutInMillis, jobject watcherCallback);

    void handleEvents(WatchPoint* watchPoint, DWORD errorCode, const vector<BYTE>& buffer, DWORD bytesTransferred);
    bool executeOnRunLoop(function<bool()> command);

    virtual void registerPaths(const vector<u16string>& paths) override;
    virtual bool unregisterPaths(const vector<u16string>& paths) override;

protected:
    void initializeRunLoop() override;
    void runLoop() override;
    void shutdownRunLoop() override;

private:
    void handleEvent(JNIEnv* env, const u16string& path, FILE_NOTIFY_INFORMATION* info);

    void registerPath(const u16string& path);
    bool unregisterPath(const u16string& path);

    HANDLE threadHandle;
    const size_t bufferSize;
    const long commandTimeoutInMillis;
    unordered_map<u16string, WatchPoint> watchPoints;
    bool shouldTerminate = false;
    friend void CALLBACK executeOnRunLoopCallback(_In_ ULONG_PTR info);
};

#endif
