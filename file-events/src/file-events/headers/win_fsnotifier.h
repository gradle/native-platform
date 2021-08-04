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
#include "net_rubygrapefruit_platform_internal_jni_WindowsFileEventFunctions_WindowsFileWatcher.h"

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

/**
 * Represents a watched directory hierarchy.
 *
 * @note When the hierarchy is moved, Windows does not send any events, and thus Java application won't
 * be notified of the change. To avoid reporting incorrect changes, we instead report the watched directory
 * being removed on the first event received after the move. To detect moved watched directories without
 * having to wait for an event to happen inside the moved directory, the Java application can call
 * stopWatchingMovedPaths().
 */
class WatchPoint {
public:
    WatchPoint(Server* server, size_t eventBufferSize, const wstring& path);
    ~WatchPoint();

    ListenResult listen();
    bool cancel();

private:
    bool isValidDirectory();
    void close();

    Server* server;
    friend class Server;

    /**
     * The path this watch point is registered with (same as the key of Server::watchPoints).
     * It is the path passed from the Java side, and it has not been canonicalized or finalized.
     *
     * For SUBST drives this holds the substed location (e.g. `G:\watched`).
     *
     * @see registeredFinalPath
     */
    const wstring registeredPath;

    /**
     * The HANDLE of the directory being watched.
     */
    HANDLE directoryHandle;

    /**
     * The final path of the watched directory at registration, according to GetFinalPathNameByHandleW.
     *
     * This is a canonicalized path that is similar to Java's File.getCanonicalizedFile() in that it
     * resoves symlinks. Unlike the Java method, GetFinalPathNameByHandleW also resolves SUBST drives
     * to the locations they point to.
     */
    wstring registeredFinalPath;

    /**
     * OVERLAPPED structure used with ReadDirectoryChangesExW.
     */
    OVERLAPPED overlapped;

    /**
     * Event buffer used with ReadDirectoryChangesExW.
     */
    vector<BYTE> eventBuffer;

    /**
     * Whether the watch point is watching, has been cancelled or fully closed.
     */
    WatchPointStatus status;

    void handleEventsInBuffer(DWORD errorCode, DWORD bytesTransferred);
    friend static void CALLBACK handleEventCallback(DWORD errorCode, DWORD bytesTransferred, LPOVERLAPPED overlapped);
};

class Server : public AbstractServer {
public:
    Server(JNIEnv* env, size_t eventBufferSize, long commandTimeoutInMillis, jobject watcherCallback);

    // List<String> droppedPaths
    void stopWatchingMovedPaths(jobject droppedPaths);

    void handleEvents(WatchPoint* watchPoint, DWORD errorCode, const vector<BYTE>& eventBuffer, DWORD bytesTransferred);
    bool executeOnRunLoop(function<bool()> command);

    virtual void registerPaths(const vector<u16string>& paths) override;
    virtual bool unregisterPaths(const vector<u16string>& paths) override;

protected:
    void initializeRunLoop() override;
    void runLoop() override;
    void shutdownRunLoop() override;

private:
    void handleEvent(JNIEnv* env, const wstring& watchedPath, FILE_NOTIFY_EXTENDED_INFORMATION* info);

    void registerPath(const u16string& path);
    bool unregisterPath(const u16string& path);

    void reportWatchPointDeleted(WatchPoint* watchPoint);

    HANDLE threadHandle;
    const size_t eventBufferSize;
    const long commandTimeoutInMillis;
    unordered_map<wstring, WatchPoint> watchPoints;
    bool shouldTerminate = false;
    friend void CALLBACK executeOnRunLoopCallback(_In_ ULONG_PTR info);
    jmethodID listAddMethod;
};

#endif
