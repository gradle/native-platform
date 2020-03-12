#ifdef _WIN32

#include <assert.h>

#include "win_fsnotifier.h"

using namespace std;

//
// WatchPoint
//

WatchPoint::WatchPoint(Server* server, const u16string& path)
    : path(path)
    , status(NOT_LISTENING) {
    wstring pathW(path.begin(), path.end());
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
        throw FileWatcherException("Couldn't add watch", path, GetLastError());
    }
    this->directoryHandle = directoryHandle;

    this->server = server;
    this->buffer.reserve(EVENT_BUFFER_SIZE);
    ZeroMemory(&this->overlapped, sizeof(OVERLAPPED));
    this->overlapped.hEvent = this;
    listen();
}

WatchPoint::~WatchPoint() {
    }

void WatchPoint::cancel() {
    if (status == LISTENING) {
        log_fine(server->getThreadEnv(), "Cancelling %s", utf16ToUtf8String(path).c_str());
        status = CANCELLED;
        BOOL ret = CancelIo(directoryHandle);
        if (!ret) {
            throw FileWatcherException("Couldn't cancel I/O", path, GetLastError());
        }
    }
}

static void CALLBACK handleEventCallback(DWORD errorCode, DWORD bytesTransferred, LPOVERLAPPED overlapped) {
    WatchPoint* watchPoint = (WatchPoint*) overlapped->hEvent;
    watchPoint->handleEventsInBuffer(errorCode, bytesTransferred);
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
    if (success) {
        status = LISTENING;
    } else {
        status = FINISHED;
        throw FileWatcherException("Couldn't start watching", path, GetLastError());
    }
}

void WatchPoint::handleEventsInBuffer(DWORD errorCode, DWORD bytesTransferred) {
    if (errorCode == ERROR_OPERATION_ABORTED) {
        log_fine(server->getThreadEnv(), "Finished watching '%s', status = %d", utf16ToUtf8String(path).c_str(), status);
        BOOL ret = CloseHandle(directoryHandle);
        if (!ret) {
            log_severe(server->getThreadEnv(), "Couldn't close handle %p for '%ls': %d", directoryHandle, utf16ToUtf8String(path).c_str(), GetLastError());
        }
        status = FINISHED;
        return;
    }

    if (status != LISTENING) {
        log_fine(server->getThreadEnv(), "Ignoring incoming events for %s as watch-point is not listening (%d bytes, errorCode = %d, status = %d)",
            utf16ToUtf8String(path).c_str(), bytesTransferred, errorCode, status);
        return;
    }
    status = NOT_LISTENING;
    server->handleEvents(this, errorCode, buffer, bytesTransferred);
}

void Server::handleEvents(WatchPoint* watchPoint, DWORD errorCode, const vector<BYTE>& buffer, DWORD bytesTransferred) {
    JNIEnv* env = getThreadEnv();
    const u16string& path = watchPoint->path;

    try {
        if (errorCode != ERROR_SUCCESS) {
            throw FileWatcherException("Error received when handling events", path, errorCode);
        }

        if (terminated) {
            log_fine(env, "Ignoring incoming events for %s because server is terminating (%d bytes)",
                utf16ToUtf8String(path).c_str(), bytesTransferred, watchPoint->status);
            return;
        }

        if (bytesTransferred == 0) {
            // Got a buffer overflow => current changes lost => send INVALIDATE on root
            log_info(env, "Detected overflow for %s", utf16ToUtf8String(path).c_str());
            reportChange(env, FILE_EVENT_INVALIDATE, path);
        } else {
            int index = 0;
            for (;;) {
                FILE_NOTIFY_INFORMATION* current = (FILE_NOTIFY_INFORMATION*) &buffer[index];
                handleEvent(env, path, current);
                if (current->NextEntryOffset == 0) {
                    break;
                }
                index += current->NextEntryOffset;
            }
        }

        watchPoint->listen();
    } catch (const exception& ex) {
        reportError(env, ex);
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

// TODO How can this be done nicer, wihtout both unnecessary copy and in-place mutation?
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

void Server::handleEvent(JNIEnv* env, const u16string& path, FILE_NOTIFY_INFORMATION* info) {
    wstring changedPathW = wstring(info->FileName, 0, info->FileNameLength / sizeof(wchar_t));
    u16string changedPath(changedPathW.begin(), changedPathW.end());
    if (!changedPath.empty()) {
        changedPath.insert(0, 1, u'\\');
    }
    changedPath.insert(0, path);
    // TODO Remove long prefix for path once?
    if (isLongPath(changedPath)) {
        if (isUncLongPath(changedPath)) {
            changedPath.erase(0, 8).insert(0, u"\\\\");
        } else {
            changedPath.erase(0, 4);
        }
    }

    log_fine(env, "Change detected: 0x%x '%ls'", info->Action, changedPathW.c_str());

    jint type;
    if (info->Action == FILE_ACTION_ADDED || info->Action == FILE_ACTION_RENAMED_NEW_NAME) {
        type = FILE_EVENT_CREATED;
    } else if (info->Action == FILE_ACTION_REMOVED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
        type = FILE_EVENT_REMOVED;
    } else if (info->Action == FILE_ACTION_MODIFIED) {
        type = FILE_EVENT_MODIFIED;
    } else {
        log_warning(env, "Unknown event 0x%x for %ls", info->Action, changedPathW.c_str());
        type = FILE_EVENT_UNKNOWN;
    }

    reportChange(env, type, changedPath);
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
    log_fine(getThreadEnv(), "Terminating", NULL);
    terminated = true;
}

Server::~Server() {
    executeOnThread(shared_ptr<Command>(new TerminateCommand()));

    if (watcherThread.joinable()) {
        watcherThread.join();
    }
}

void Server::runLoop(JNIEnv* env, function<void(exception_ptr)> notifyStarted) {
    notifyStarted(nullptr);

    while (!terminated) {
        SleepEx(INFINITE, true);
    }

    // We have received termination, cancel all watchers
    log_fine(env, "Finished with run loop, now cancelling remaining watch points", NULL);
    int openWatchPoints = 0;
    for (auto& it : watchPoints) {
        auto& watchPoint = it.second;
        switch (watchPoint.status) {
            case LISTENING:
                watchPoint.cancel();
                openWatchPoints++;
                break;
            case CANCELLED:
                openWatchPoints++;
                break;
            default:
                break;
        }
    }

    // If there are any remaining watchers, wait for them to be terminated
    if (openWatchPoints == 0) {
        log_fine(env, "No watch points were open upon termination", NULL);
    } else {
        log_fine(env, "Waiting for %d watch points to abort", openWatchPoints);
        SleepEx(SERVER_CLOSE_TIMEOUT_IN_MS, true);
    }

    // Check if we have any unclosed watchpoints
    for (auto& it : watchPoints) {
        auto& watchPoint = it.second;
        switch (watchPoint.status) {
            case FINISHED:
                break;
            default:
                log_severe(env, "Couldn't close watch point %s, status = %d", utf16ToUtf8String(watchPoint.path).c_str(), watchPoint.status);
                break;
        }
    }

    // Close all handles
    log_fine(env, "Closing handles for %d watch points", watchPoints.size());
    watchPoints.clear();
}

static void CALLBACK processCommandsCallback(_In_ ULONG_PTR info) {
    Server* server = (Server*) info;
    server->processCommands();
}

void Server::processCommandsOnThread() {
    QueueUserAPC(processCommandsCallback, watcherThread.native_handle(), (ULONG_PTR) this);
}

void Server::registerPath(const u16string& path) {
    u16string longPath = path;
    convertToLongPathIfNeeded(longPath);
    if (watchPoints.find(longPath) != watchPoints.end()) {
        throw FileWatcherException("Already watching path", path);
    }
    watchPoints.emplace(piecewise_construct,
        forward_as_tuple(longPath),
        forward_as_tuple(this, longPath));
}

void Server::unregisterPath(const u16string& path) {
    u16string longPath = path;
    convertToLongPathIfNeeded(longPath);
    auto it = watchPoints.find(longPath);
    if (it == watchPoints.end()) {
        log_fine(getThreadEnv(), "Path is not watched: %s", utf16ToUtf8String(path).c_str());
        return;
    }
    it->second.cancel();
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
