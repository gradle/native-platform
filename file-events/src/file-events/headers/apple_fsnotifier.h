#pragma once

#if defined(__APPLE__)

#include <CoreServices/CoreServices.h>
#include <unordered_map>

#include "generic_fsnotifier.h"
#include "net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions.h"

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

    virtual void registerPaths(const vector<u16string>& paths) override;
    virtual bool unregisterPaths(const vector<u16string>& paths) override;

protected:
    void initializeRunLoop() override;
    void runLoop() override;

    void shutdownRunLoop() override;

private:
    void handleEvent(JNIEnv* env, char* path, FSEventStreamEventFlags flags, FSEventStreamEventId eventId);
    void handleEvents(
        size_t numEvents,
        char** eventPaths,
        const FSEventStreamEventFlags eventFlags[],
        const FSEventStreamEventId eventIds[]);

    friend void handleEventsCallback(
        ConstFSEventStreamRef stream,
        void* clientCallBackInfo,
        size_t numEvents,
        void* eventPaths,
        const FSEventStreamEventFlags eventFlags[],
        const FSEventStreamEventId eventIds[]);

    const long latencyInMillis;
    recursive_mutex mutationMutex;
    unordered_map<u16string, WatchPoint> watchPoints;

    CFRunLoopRef threadLoop;
    CFRunLoopSourceRef messageSource;
};

#endif
