#pragma once

#ifdef _WIN32

#include <Shlwapi.h>
#include <string>
#include <unordered_map>
#include <vector>
#include <wchar.h>
#include <windows.h>

// Needs to stay below <windows.h> otherwise byte symbol gets confused with std::byte
#include "generic_fsnotifier.h"
#include "net_rubygrapefruit_platform_internal_jni_WindowsFileEventFunctions.h"

using namespace std;

#define EVENT_BUFFER_SIZE (16 * 1024)

#define CREATE_SHARE (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE)
#define CREATE_FLAGS (FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OVERLAPPED)

#define EVENT_MASK (FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME | FILE_NOTIFY_CHANGE_ATTRIBUTES | FILE_NOTIFY_CHANGE_SIZE | FILE_NOTIFY_CHANGE_LAST_WRITE)

// TODO Make this parametrizable perhaps
#define SERVER_CLOSE_TIMEOUT_IN_MS 1000

class Server;
class WatchPoint;
enum WatchPointStatus {
    /**
     * The watch point has been constructed, but not yet listening.
     */
    CONSTRUCTED,

    /**
     * The watch point is listening, expect events to arrive.
     */
    LISTENING,

    /**
     * The watch point has stopped listening and is handling events.
     */
    HANDLING,

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
    WatchPoint(Server* server, const u16string& path);
    ~WatchPoint();
    void listen();
    void cancel();

private:
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
    Server(JNIEnv* env, jobject watcherCallback);
    ~Server();

    void registerPath(const u16string& path) override;
    void unregisterPath(const u16string& path) override;
    void terminate() override;

    void handleEvents(WatchPoint* watchPoint, DWORD errorCode, const vector<BYTE>& buffer, DWORD bytesTransferred);

protected:
    void runLoop(JNIEnv* env, function<void(exception_ptr)> notifyStarted) override;
    void processCommandsOnThread() override;

private:
    void handleEvent(JNIEnv* env, const u16string& path, FILE_NOTIFY_INFORMATION* info);

    unsigned int countOpenWatchPoints();

    unordered_map<u16string, WatchPoint> watchPoints;
    bool terminated = false;
};

#endif
