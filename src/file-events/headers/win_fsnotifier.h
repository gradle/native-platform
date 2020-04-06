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
#include "net_rubygrapefruit_platform_internal_jni_fileevents_WindowsFileEventFunctions.h"

using namespace std;

#define EVENT_BUFFER_SIZE (16 * 1024)

#define CREATE_SHARE (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE)
#define CREATE_FLAGS (FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OVERLAPPED)

#define EVENT_MASK (FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME | FILE_NOTIFY_CHANGE_ATTRIBUTES | FILE_NOTIFY_CHANGE_SIZE | FILE_NOTIFY_CHANGE_LAST_WRITE)

class Server;
class WatchPoint;

enum ListenResult {
    /**
     * Listening succeeded.
     */
    SUCCESS,
    /**
     * Target directory has been removed.
     */
    DELETED
};

class WatchPoint {
public:
    WatchPoint(Server* server, const u16string& path);
    ~WatchPoint();

    ListenResult listen();
    bool cancel();

private:
    bool isValidDirectory();

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
    bool unregisterPath(const u16string& path) override;
    void terminate() override;

    void handleEvents(WatchPoint* watchPoint, DWORD errorCode, const vector<BYTE>& buffer, DWORD bytesTransferred);

protected:
    void runLoop(function<void(exception_ptr)> notifyStarted) override;
    void processCommandsOnThread() override;

private:
    void handleEvent(JNIEnv* env, const u16string& path, FILE_NOTIFY_INFORMATION* info);

    unordered_map<u16string, WatchPoint> watchPoints;
    bool terminated = false;
};

#endif
