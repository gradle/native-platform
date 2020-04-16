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
    WatchPoint(Server* server, size_t bufferSize, const u16string& path);
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
    Server(JNIEnv* env, size_t bufferSize, jobject watcherCallback);

    void handleEvents(WatchPoint* watchPoint, DWORD errorCode, const vector<BYTE>& buffer, DWORD bytesTransferred);
    bool executeOnRunLoop(function<bool()> command);

    virtual void registerPaths(const vector<u16string>& paths) override;
    virtual bool unregisterPaths(const vector<u16string>& paths) override;

protected:
    void initializeRunLoop() override;
    void runLoop() override;

    void registerPath(const u16string& path) override;
    bool unregisterPath(const u16string& path) override;
    void terminateRunLoop() override;

private:
    void handleEvent(JNIEnv* env, const u16string& path, FILE_NOTIFY_INFORMATION* info);

    HANDLE threadHandle;
    const size_t bufferSize;
    unordered_map<u16string, WatchPoint> watchPoints;
    bool terminated = false;
    mutex executionMutex;
    friend void CALLBACK executeOnRunLoopCallback(_In_ ULONG_PTR info);
};

#endif
