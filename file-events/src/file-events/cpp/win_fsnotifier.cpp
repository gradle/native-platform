#ifdef _WIN32

#include "win_fsnotifier.h"
#include "command.h"

#include <codecvt>
#include <locale>

using namespace std;

string wideToUtf8String(const wstring& string) {
    wstring_convert<deletable_facet<codecvt<wchar_t, char, mbstate_t>>, wchar_t> conv;
    return conv.to_bytes(string);
}

#define wideToUtf16String(string) (u16string((string).begin(), (string).end()))

bool isAbsoluteLocalPath(const wstring& path) {
    if (path.length() < 3) {
        return false;
    }
    return ((L'a' <= path[0] && path[0] <= L'z') || (L'A' <= path[0] && path[0] <= L'Z'))
        && path[1] == L':'
        && path[2] == L'\\';
}

bool isAbsoluteUncPath(const wstring& path) {
    if (path.length() < 3) {
        return false;
    }
    return path[0] == L'\\' && path[1] == L'\\';
}

bool isLongPath(const wstring& path) {
    return path.length() >= 4 && path.substr(0, 4) == L"\\\\?\\";
}

bool isUncLongPath(const wstring& path) {
    return path.length() >= 8 && path.substr(0, 8) == L"\\\\?\\UNC\\";
}

// TODO How can this be done nicer, wihtout both unnecessary copy and in-place mutation?
void convertToLongPathIfNeeded(wstring& path) {
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
        path.insert(0, L"\\\\?\\");
    } else if (isAbsoluteUncPath(path)) {
        // In this case, we need to skip the first 2 characters:
        // Format: \\server\share\... -> \\?\UNC\server\share\...
        path.erase(0, 2);
        path.insert(0, L"\\\\?\\UNC\\");
    } else {
        // It is some sort of unknown format, don't mess with it
    }
}

void convertFromLongPathIfNeeded(wstring& path) {
    if (isLongPath(path)) {
        if (isUncLongPath(path)) {
            path.erase(0, 8).insert(0, L"\\\\");
        } else {
            path.erase(0, 4);
        }
    }
}

//
// WatchPoint
//

WatchPoint::WatchPoint(Server* server, size_t eventBufferSize, const wstring& path)
    : path(path)
    , status(WatchPointStatus::NOT_LISTENING)
    , server(server) {
    HANDLE directoryHandle = CreateFileW(
        path.c_str(),          // pointer to the file name
        FILE_LIST_DIRECTORY,    // access (read/write) mode
        CREATE_SHARE,           // share mode
        NULL,                   // security descriptor
        OPEN_EXISTING,          // how to create
        CREATE_FLAGS,           // file attributes
        NULL                    // file with attributes to copy
    );
    if (directoryHandle == INVALID_HANDLE_VALUE) {
        throw FileWatcherException("Couldn't add watch", wideToUtf16String(path), GetLastError());
    }
    this->directoryHandle = directoryHandle;
    this->eventBuffer.reserve(eventBufferSize);
    ZeroMemory(&this->overlapped, sizeof(OVERLAPPED));
    this->overlapped.hEvent = this;
    switch (listen()) {
        case ListenResult::SUCCESS:
            break;
        case ListenResult::DELETED:
            throw FileWatcherException("Couldn't start watching because path is not a directory", wideToUtf16String(path));
    }
}

bool WatchPoint::cancel() {
    if (status == WatchPointStatus::LISTENING) {
        logToJava(LogLevel::FINE, "Cancelling %s", wideToUtf8String(path).c_str());
        bool cancelled = (bool) CancelIoEx(directoryHandle, &overlapped);
        if (cancelled) {
            status = WatchPointStatus::CANCELLED;
        } else {
            DWORD cancelError = GetLastError();
            close();
            if (cancelError == ERROR_NOT_FOUND) {
                // Do nothing, looks like this is a typical scenario
                logToJava(LogLevel::FINE, "Watch point already finished %s", wideToUtf8String(path).c_str());
            } else {
                throw FileWatcherException("Couldn't cancel watch point", wideToUtf16String(path), cancelError);
            }
        }
        return cancelled;
    }
    return false;
}

WatchPoint::~WatchPoint() {
    try {
        cancel();
        SleepEx(0, true);
        close();
    } catch (const exception& ex) {
        logToJava(LogLevel::WARNING, "Couldn't cancel watch point %s: %s", wideToUtf8String(path).c_str(), ex.what());
    }
}

// Allocate maximum path length
#define PATH_BUFFER_SIZE 32768

wstring WatchPoint::getPath() {
    vector<wchar_t> buffer;
    buffer.reserve(PATH_BUFFER_SIZE);
    DWORD pathLength = GetFinalPathNameByHandleW(
        this->directoryHandle,
        &buffer[0],
        PATH_BUFFER_SIZE,
        FILE_NAME_OPENED
    );
    if (pathLength == 0 || pathLength > PATH_BUFFER_SIZE) {
        throw FileWatcherException("Error received when resolving file path", GetLastError());
    }
    return wstring(&buffer[0], 0, pathLength);
}

static void CALLBACK handleEventCallback(DWORD errorCode, DWORD bytesTransferred, LPOVERLAPPED overlapped) {
    WatchPoint* watchPoint = (WatchPoint*) overlapped->hEvent;
    watchPoint->handleEventsInBuffer(errorCode, bytesTransferred);
}

bool WatchPoint::isValidDirectory() {
    DWORD attrib = GetFileAttributesW(path.c_str());

    return (attrib != INVALID_FILE_ATTRIBUTES)
        && ((attrib & FILE_ATTRIBUTE_DIRECTORY) != 0);
}

ListenResult WatchPoint::listen() {
    BOOL success = ReadDirectoryChangesExW(
        directoryHandle,              // handle to directory
        &eventBuffer[0],                   // read results buffer
        (DWORD) eventBuffer.capacity(),    // length of buffer
        TRUE,                         // include children
        EVENT_MASK,                   // filter conditions
        NULL,                         // bytes returned
        &overlapped,                  // overlapped buffer
        &handleEventCallback,         // completion routine
        ReadDirectoryNotifyExtendedInformation
    );
    if (success) {
        status = WatchPointStatus::LISTENING;
        return ListenResult::SUCCESS;
    } else {
        DWORD listenError = GetLastError();
        close();
        if (listenError == ERROR_ACCESS_DENIED && !isValidDirectory()) {
            return ListenResult::DELETED;
        } else {
            throw FileWatcherException("Couldn't start watching", wideToUtf16String(path), listenError);
        }
    }
}

void WatchPoint::close() {
    if (status != WatchPointStatus::FINISHED) {
        try {
            BOOL ret = CloseHandle(directoryHandle);
            if (!ret) {
                logToJava(LogLevel::SEVERE, "Couldn't close handle %p for '%ls': %d", directoryHandle, wideToUtf8String(path).c_str(), GetLastError());
            }
        } catch (const exception& ex) {
            // Apparently with debugging enabled CloseHandle() can also throw, see:
            // https://docs.microsoft.com/en-us/windows/win32/api/handleapi/nf-handleapi-closehandle#return-value
            logToJava(LogLevel::SEVERE, "Couldn't close handle %p for '%ls': %s", directoryHandle, wideToUtf8String(path).c_str(), ex.what());
        }
        status = WatchPointStatus::FINISHED;
    }
}

void WatchPoint::handleEventsInBuffer(DWORD errorCode, DWORD bytesTransferred) {
    if (errorCode == ERROR_OPERATION_ABORTED) {
        logToJava(LogLevel::FINE, "Finished watching '%s', status = %d", wideToUtf8String(path).c_str(), status);
        close();
        return;
    }

    if (status != WatchPointStatus::LISTENING) {
        logToJava(LogLevel::FINE, "Ignoring incoming events for %s as watch-point is not listening (%d bytes, errorCode = %d, status = %d)",
            wideToUtf8String(path).c_str(), bytesTransferred, errorCode, status);
        return;
    }
    status = WatchPointStatus::NOT_LISTENING;
    server->handleEvents(this, errorCode, eventBuffer, bytesTransferred);
}

//
// Server
//

void Server::handleEvents(WatchPoint* watchPoint, DWORD errorCode, const vector<BYTE>& eventBuffer, DWORD bytesTransferred) {
    JNIEnv* env = getThreadEnv();
    wstring path = watchPoint->getPath();
    convertFromLongPathIfNeeded(path);

    try {
        if (errorCode != ERROR_SUCCESS) {
            if (errorCode == ERROR_ACCESS_DENIED && !watchPoint->isValidDirectory()) {
                reportChangeEvent(env, ChangeType::REMOVED, wideToUtf16String(path));
                watchPoint->close();
                return;
            } else {
                throw FileWatcherException("Error received when handling events", wideToUtf16String(path), errorCode);
            }
        }

        if (shouldTerminate) {
            logToJava(LogLevel::FINE, "Ignoring incoming events for %s because server is terminating (%d bytes, status = %d)",
                wideToUtf8String(path).c_str(), bytesTransferred, watchPoint->status);
            return;
        }

        if (bytesTransferred == 0) {
            // This is what the documentation has to say about a zero-length dataset:
            //
            //     If the number of bytes transferred is zero, the eventBuffer was either too large
            //     for the system to allocate or too small to provide detailed information on
            //     all the changes that occurred in the directory or subtree. In this case,
            //     you should compute the changes by enumerating the directory or subtree.
            //
            // (See https://docs.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-readdirectorychangesw)
            //
            // We'll handle this as a simple overflow and report it as such.
            reportOverflow(env, wideToUtf16String(path));
        } else {
            int index = 0;
            for (;;) {
                FILE_NOTIFY_EXTENDED_INFORMATION* current = (FILE_NOTIFY_EXTENDED_INFORMATION*) &eventBuffer[index];
                handleEvent(env, path, current);
                if (current->NextEntryOffset == 0) {
                    break;
                }
                index += current->NextEntryOffset;
            }
        }

        switch (watchPoint->listen()) {
            case ListenResult::SUCCESS:
                break;
            case ListenResult::DELETED:
                logToJava(LogLevel::FINE, "Watched directory removed for %s", wideToUtf8String(path).c_str());
                reportChangeEvent(env, ChangeType::REMOVED, wideToUtf16String(path));
                break;
        }
    } catch (const exception& ex) {
        reportFailure(env, ex);
    }
}

void Server::handleEvent(JNIEnv* env, const wstring& watchedPathW, FILE_NOTIFY_EXTENDED_INFORMATION* info) {
    wstring changedPathW = wstring(info->FileName, 0, info->FileNameLength / sizeof(wchar_t));
    if (!changedPathW.empty()) {
        changedPathW.insert(0, 1, L'\\');
    }
    changedPathW.insert(0, watchedPathW);
    convertFromLongPathIfNeeded(changedPathW);

    logToJava(LogLevel::FINE, "Change detected: 0x%x '%s'", info->Action, wideToUtf8String(changedPathW).c_str());

    ChangeType type;
    if (info->Action == FILE_ACTION_ADDED || info->Action == FILE_ACTION_RENAMED_NEW_NAME) {
        type = ChangeType::CREATED;
    } else if (info->Action == FILE_ACTION_REMOVED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
        type = ChangeType::REMOVED;
    } else if (info->Action == FILE_ACTION_MODIFIED) {
        if (info->FileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
            // Ignore MODIFIED events on directories
            logToJava(LogLevel::FINE, "Ignored MODIFIED event on directory", nullptr);
            return;
        }
        type = ChangeType::MODIFIED;
    } else {
        logToJava(LogLevel::WARNING, "Unknown event 0x%x for %s", info->Action, wideToUtf8String(changedPathW).c_str());
        reportUnknownEvent(env, wideToUtf16String(changedPathW));
        return;
    }

    reportChangeEvent(env, type, wideToUtf16String(changedPathW));
}

//
// Server
//

Server::Server(JNIEnv* env, size_t eventBufferSize, long commandTimeoutInMillis, jobject watcherCallback)
    : AbstractServer(env, watcherCallback)
    , eventBufferSize(eventBufferSize)
    , commandTimeoutInMillis(commandTimeoutInMillis) {
}

void Server::initializeRunLoop() {
    // For some reason GetCurrentThread() returns a thread that doesn't accept APCs
    // so we need to use OpenThread() instead.
    threadHandle = OpenThread(
        THREAD_ALL_ACCESS,      // dwDesiredAccess
        false,                  // bInheritHandle
        GetCurrentThreadId()    // dwThreadId
    );
    if (threadHandle == NULL) {
        throw FileWatcherException("Couldn't open current thread", GetLastError());
    }
}

void Server::shutdownRunLoop() {
    executeOnRunLoop([this]() {
        shouldTerminate = true;
        return true;
    });
}

void Server::runLoop() {
    while (!shouldTerminate) {
        SleepEx(INFINITE, true);
    }

    // We have received termination, cancel all watchers
    logToJava(LogLevel::FINE, "Finished with run loop, now cancelling remaining watch points", NULL);
    for (auto& watchPoint : watchPoints) {
        if (watchPoint.status == WatchPointStatus::LISTENING) {
            try {
                watchPoint.cancel();
            } catch (const exception& ex) {
                logToJava(LogLevel::SEVERE, "%s", ex.what());
            }
        }
    }

    logToJava(LogLevel::FINE, "Waiting for any pending watch points to abort completely", NULL);
    SleepEx(0, true);

    // Warn about  any unfinished watchpoints
    for (auto& watchPoint : watchPoints) {
        switch (watchPoint.status) {
            case WatchPointStatus::NOT_LISTENING:
            case WatchPointStatus::FINISHED:
                break;
            default:
                logToJava(LogLevel::WARNING, "Watch point %s did not finish before termination timeout (status = %d)",
                    wideToUtf8String(watchPoint.path).c_str(), watchPoint.status);
                break;
        }
    }

    CloseHandle(threadHandle);
}

static void CALLBACK executeOnRunLoopCallback(_In_ ULONG_PTR info) {
    Command* command = (Command*) info;
    command->executeInsideRunLoop();
}

bool Server::executeOnRunLoop(function<bool()> function) {
    Command command(function);
    return command.execute(commandTimeoutInMillis, [this](Command* command) {
        DWORD ret = QueueUserAPC(executeOnRunLoopCallback, threadHandle, (ULONG_PTR) command);
        if (ret == 0) {
            throw FileWatcherException("Received error while queuing APC", GetLastError());
        }
    });
}

void Server::registerPaths(const vector<u16string>& paths) {
    executeOnRunLoop([this, paths]() {
        for (auto& path : paths) {
            registerPath(path);
        }
        return true;
    });
}

bool Server::unregisterPaths(const vector<u16string>& paths) {
    return executeOnRunLoop([this, paths]() {
        bool success = true;
        for (auto& path : paths) {
            success &= unregisterPath(path);
        }
        return success;
    });
}

void Server::registerPath(const u16string& path) {
    wstring longPathW(path.begin(), path.end());
    convertToLongPathIfNeeded(longPathW);
    for (auto it = watchPoints.begin(); it != watchPoints.end(); ++it) {
        if (it->path != longPathW) {
            continue;
        }
        if (it->status == WatchPointStatus::FINISHED) {
            watchPoints.erase(it);
            break;
        } else {
            throw FileWatcherException("Already watching path", path);
        }
    }
    watchPoints.emplace_back(this, eventBufferSize, longPathW);
}

bool Server::unregisterPath(const u16string& path) {
    wstring longPathW(path.begin(), path.end());
    convertToLongPathIfNeeded(longPathW);
    for (auto it = watchPoints.begin(); it != watchPoints.end(); ++it) {
        if (it->path != longPathW) {
            continue;
        }
        watchPoints.erase(it);
        return true;
    }
    logToJava(LogLevel::INFO, "Path is not watched: %s", wideToUtf8String(longPathW).c_str());
    return false;
}

//
// JNI calls
//

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_WindowsFileEventFunctions_startWatcher0(JNIEnv* env, jclass target, jint eventBufferSize, jlong commandTimeoutInMillis, jobject javaCallback) {
    return wrapServer(env, new Server(env, eventBufferSize, (long) commandTimeoutInMillis, javaCallback));
}

#endif
