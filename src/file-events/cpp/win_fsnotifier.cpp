#ifdef _WIN32

#include <assert.h>

#include "win_fsnotifier.h"

using namespace std;

//
// WatchPoint
//

static void CALLBACK listenCallback(_In_ ULONG_PTR arg) {
    WatchPoint* watchPoint = (WatchPoint*) arg;
    watchPoint->listen();
}

WatchPoint::WatchPoint(Server* server, const u16string& path, HANDLE directoryHandle, HANDLE serverThreadHandle) {
    this->server = server;
    this->path = path;
    this->buffer.reserve(EVENT_BUFFER_SIZE);
    ZeroMemory(&this->overlapped, sizeof(OVERLAPPED));
    this->overlapped.hEvent = this;
    this->directoryHandle = directoryHandle;
    listen();
}

WatchPoint::~WatchPoint() {
}

void WatchPoint::close() {
    BOOL ret = CancelIo(directoryHandle);
    if (!ret) {
        log_severe(server->getThreadEnv(), "Couldn't cancel I/O %p for '%ls': %d", directoryHandle, path.c_str(), GetLastError());
    }
    ret = CloseHandle(directoryHandle);
    if (!ret) {
        log_severe(server->getThreadEnv(), "Couldn't close handle %p for '%ls': %d", directoryHandle, path.c_str(), GetLastError());
    }
}

static void CALLBACK handleEventCallback(DWORD errorCode, DWORD bytesTransferred, LPOVERLAPPED overlapped) {
    WatchPoint* watchPoint = (WatchPoint*) overlapped->hEvent;

    if (errorCode == ERROR_OPERATION_ABORTED) {
        Server* server = watchPoint->server;
        log_fine(server->getThreadEnv(), "Finished watching '%ls'", watchPoint->path.c_str());
        server->reportFinished(*watchPoint);
        return;
    }
    // TODO Handle other error codes

    watchPoint->handleEvent(bytesTransferred);
}

void WatchPoint::listen() {
    BOOL success = ReadDirectoryChangesW(
        directoryHandle,        // handle to directory
        &buffer[0],             // read results buffer
        EVENT_BUFFER_SIZE,      // length of buffer
        TRUE,                   // include children
        EVENT_MASK,             // filter conditions
        NULL,                   // bytes returned
        &overlapped,            // overlapped buffer
        &handleEventCallback    // completion routine
    );
    if (!success) {
        log_warning(server->getThreadEnv(), "Couldn't start watching %p for '%ls', error = %d", directoryHandle, path.c_str(), GetLastError());
        throw FileWatcherException("Couldn't start watching");
    }
}

void WatchPoint::handleEvent(DWORD bytesTransferred) {
    if (bytesTransferred == 0) {
        // Got a buffer overflow => current changes lost => send INVALIDATE on root
        log_info(server->getThreadEnv(), "Detected overflow for %ls", path.c_str());
        server->reportEvent(FILE_EVENT_INVALIDATE, path);
    } else {
        int index = 0;
        for (;;) {
            FILE_NOTIFY_INFORMATION* current = (FILE_NOTIFY_INFORMATION*) &buffer[index];
            handlePathChanged(current);
            if (current->NextEntryOffset == 0) {
                fprintf(stderr, ">>>> Done\n");
                break;
            }
            index += current->NextEntryOffset;
        }
    }

    try {
        listen();
    } catch (const exception& e) {
        log_severe(server->getThreadEnv(), "Watching failed: %s", e.what());
        server->reportFinished(*this);
    }
}

bool isAbsoluteLocalPath(const u16string& path) {
    if (path.length() < 3) {
        return false;
    }
    return ((u'a' <= path[0] && path[0] <= u'z') || (u'A' <= path[0] && path[0] <= u'Z'))
        && path[1] == u':'
        && path[2] == u'\\';
}

bool isAbsoluteUncPath(const u16string& path) {
    if (path.length() < 3) {
        return false;
    }
    return path[0] == u'\\' && path[1] == u'\\';
}

bool isLongPath(const u16string& path) {
    return path.length() >= 4 && path.substr(0, 4) == u"\\\\?\\";
}

bool isUncLongPath(const u16string& path) {
    return path.length() >= 8 && path.substr(0, 8) == u"\\\\?\\UNC\\";
}

void convertToLongPathIfNeeded(u16string& path) {
    // Technically, this should be MAX_PATH (i.e. 260), except some Win32 API related
    // to working with directory paths are actually limited to 240. It is just
    // safer/simpler to cover both cases in one code path.
    if (path.length() <= 240) {
        return;
    }

    // It is already a long path, nothing to do here
    if (isLongPath(path)) {
        return;
    }

    if (isAbsoluteLocalPath(path)) {
        // Format: C:\... -> \\?\C:\...
        path.insert(0, u"\\\\?\\");
    } else if (isAbsoluteUncPath(path)) {
        // In this case, we need to skip the first 2 characters:
        // Format: \\server\share\... -> \\?\UNC\server\share\...
        path.erase(0, 2);
        path.insert(0, u"\\\\?\\UNC\\");
    } else {
        // It is some sort of unknown format, don't mess with it
    }
}

void WatchPoint::handlePathChanged(FILE_NOTIFY_INFORMATION* info) {
    wstring changedPathW = wstring(info->FileName, 0, info->FileNameLength / sizeof(wchar_t));
    u16string changedPath(changedPathW.begin(), changedPathW.end());
    // TODO Do we ever get an empty path?
    if (!changedPath.empty()) {
        changedPath.insert(0, 1, u'\\');
        changedPath.insert(0, path);
    }
    // TODO Remove long prefix for path once?
    if (isLongPath(changedPath)) {
        if (isUncLongPath(changedPath)) {
            changedPath.erase(0, 8).insert(0, u"\\\\");
        } else {
            changedPath.erase(0, 4);
        }
    }

    log_fine(server->getThreadEnv(), "Change detected: 0x%x '%ls'", info->Action, changedPathW.c_str());

    jint type;
    if (info->Action == FILE_ACTION_ADDED || info->Action == FILE_ACTION_RENAMED_NEW_NAME) {
        type = FILE_EVENT_CREATED;
    } else if (info->Action == FILE_ACTION_REMOVED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
        type = FILE_EVENT_REMOVED;
    } else if (info->Action == FILE_ACTION_MODIFIED) {
        type = FILE_EVENT_MODIFIED;
    } else {
        log_warning(server->getThreadEnv(), "Unknown event 0x%x for %ls", info->Action, changedPathW.c_str());
        type = FILE_EVENT_UNKNOWN;
    }

    server->reportEvent(type, changedPath);
}

//
// Server
//

Server::Server(JNIEnv* env, jobject watcherCallback)
    : AbstractServer(env, watcherCallback) {
    startThread();
    // TODO Error handling
    SetThreadPriority(this->watcherThread.native_handle(), THREAD_PRIORITY_ABOVE_NORMAL);
}

void Server::terminate() {
    terminated = true;
}

Server::~Server() {
    list<u16string> paths;
    for (auto& watchPoint : watchPoints) {
        paths.push_back(watchPoint.first);
    }
    for (auto& path : paths) {
        executeOnThread(shared_ptr<Command>(new UnregisterPathCommand(path)));
    }
    executeOnThread(shared_ptr<Command>(new TerminateCommand()));

    if (watcherThread.joinable()) {
        watcherThread.join();
    }
}

void Server::runLoop(JNIEnv* env, function<void(exception_ptr)> notifyStarted) {
    notifyStarted(nullptr);

    while (!terminated || watchPoints.size() > 0) {
        SleepEx(INFINITE, true);
    }
}

static void CALLBACK processCommandsCallback(_In_ ULONG_PTR info) {
    Server* server = (Server*) info;
    server->processCommands();
}

void Server::wakeUpRunLoop() {
    QueueUserAPC(processCommandsCallback, watcherThread.native_handle(), (ULONG_PTR) this);
}

void Server::registerPath(const u16string& path) {
    u16string longPath = path;
    convertToLongPathIfNeeded(longPath);
    if (watchPoints.find(longPath) != watchPoints.end()) {
        throw FileWatcherException("Already watching path");
    }
    wstring pathW(longPath.begin(), longPath.end());
    HANDLE directoryHandle = CreateFileW(
        pathW.c_str(),          // pointer to the file name
        FILE_LIST_DIRECTORY,    // access (read/write) mode
        CREATE_SHARE,           // share mode
        NULL,                   // security descriptor
        OPEN_EXISTING,          // how to create
        CREATE_FLAGS,           // file attributes
        NULL                    // file with attributes to copy
    );

    if (directoryHandle == INVALID_HANDLE_VALUE) {
        log_severe(getThreadEnv(), "Couldn't get file handle for '%ls': %d", pathW.c_str(), GetLastError());
        // TODO Error handling
        return;
    }

    HANDLE threadHandle = GetCurrentThread();
    watchPoints.emplace(piecewise_construct,
        forward_as_tuple(longPath),
        forward_as_tuple(this, longPath, directoryHandle, threadHandle));
}

void Server::unregisterPath(const u16string& path) {
    u16string longPath = path;
    convertToLongPathIfNeeded(longPath);
    auto it = watchPoints.find(longPath);
    if (it == watchPoints.end()) {
        throw FileWatcherException("Cannot stop watching path that was never watched");
    }
    it->second.close();
}

void Server::reportFinished(const WatchPoint& watchPoint) {
    u16string path = watchPoint.path;
    watchPoints.erase(path);
}

void Server::reportEvent(jint type, const u16string& changedPath) {
    JNIEnv* env = getThreadEnv();
    reportChange(env, type, changedPath);
}

//
// JNI calls
//

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsFileEventFunctions_startWatcher(JNIEnv* env, jclass target, jobject javaCallback) {
    return wrapServer(env, [env, javaCallback]() {
        return new Server(env, javaCallback);
    });
}

#endif
