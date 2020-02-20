#pragma once

#if defined(__APPLE__)

#include "generic_fsnotifier.h"
#include "net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions.h"
#include <CoreServices/CoreServices.h>

using namespace std;

class Server;

static void handleEventsCallback(
    ConstFSEventStreamRef streamRef,
    void* clientCallBackInfo,
    size_t numEvents,
    void* eventPaths,
    const FSEventStreamEventFlags eventFlags[],
    const FSEventStreamEventId eventIds[]);

class EventStream {
public:
    EventStream(CFArrayRef rootsToWatch, long latencyInMillis);
    ~EventStream();

    void schedule(Server* server, CFRunLoopRef runLoop);
    void unschedule();

private:
    friend void handleEventsCallback(
        ConstFSEventStreamRef streamRef,
        void* clientCallBackInfo,
        size_t numEvents,
        void* eventPaths,
        const FSEventStreamEventFlags eventFlags[],
        const FSEventStreamEventId eventIds[]);

    FSEventStreamRef watcherStream;
    Server* server;
};

class Server : AbstractServer {
public:
    Server(JNIEnv* env, jobject watcherCallback, CFArrayRef rootsToWatch, long latencyInMillis);
    ~Server();

    void handleEvents(
        size_t numEvents,
        char** eventPaths,
        const FSEventStreamEventFlags eventFlags[],
        const FSEventStreamEventId eventIds[]);

protected:
    void runLoop(JNIEnv* env, function<void()> notifyStarted) override;

private:
    void handleEvent(JNIEnv* env, char* path, FSEventStreamEventFlags flags);

    EventStream eventStream;
    CFRunLoopRef threadLoop;
};

#endif
