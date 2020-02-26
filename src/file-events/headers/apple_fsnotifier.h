#pragma once

#if defined(__APPLE__)

#include <CoreServices/CoreServices.h>
#include <unordered_map>

#include "generic_fsnotifier.h"
#include "net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions_WatcherImpl.h"

using namespace std;

class Server;

static void handleEventsCallback(
    ConstFSEventStreamRef streamRef,
    void* clientCallBackInfo,
    size_t numEvents,
    void* eventPaths,
    const FSEventStreamEventFlags eventFlags[],
    const FSEventStreamEventId*);

class WatchPoint {
public:
    WatchPoint(Server* server, CFRunLoopRef runLoop, const u16string& path, long latencyInMillis);
    ~WatchPoint();

private:
    FSEventStreamRef watcherStream;
};

class Server : public AbstractServer {
public:
    Server(JNIEnv* env, jobject watcherCallback, long latencyInMillis);
    ~Server();

    void startWatching(const u16string& path);
    void stopWatching(const u16string& path);

    // TODO This should be private
    void handleEvents(
        size_t numEvents,
        char** eventPaths,
        const FSEventStreamEventFlags eventFlags[]);

protected:
    void runLoop(JNIEnv* env, function<void(exception_ptr)> notifyStarted) override;

private:
    void handleEvent(JNIEnv* env, char* path, FSEventStreamEventFlags flags);

    const long latencyInMillis;
    unordered_map<u16string, WatchPoint> watchPoints;
    CFRunLoopRef threadLoop;
    CFRunLoopTimerRef keepAlive;
};

#endif
