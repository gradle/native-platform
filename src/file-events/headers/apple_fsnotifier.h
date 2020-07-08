#pragma once

#if defined(__APPLE__)

#include <CoreServices/CoreServices.h>
#include <queue>
#include <unordered_set>

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

class Server : public AbstractServer {
public:
    Server(JNIEnv* env, jobject watcherCallback, long latencyInMillis, long commandTimeoutInMillis);

protected:
    void initializeRunLoop() override;
    void runLoop() override;
    virtual void queueOnRunLoop(Command* command) override;

    virtual void registerPathsInternal(const vector<u16string>& paths) override;
    virtual bool unregisterPathsInternal(const vector<u16string>& paths) override;

    void shutdownRunLoop() override;

private:
    void updateEventStream();
    void createEventStream();
    void closeEventStream();

    void handleCommands();
    friend void acceptTrigger(void* info);

    void handleEvent(JNIEnv* env, char* path, FSEventStreamEventFlags flags);
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
    FSEventStreamEventId lastSeenEventId = kFSEventStreamEventIdSinceNow;
    unordered_set<u16string> watchPoints;
    FSEventStreamRef eventStream = nullptr;

    mutex commandMutex;
    queue<Command*> commands;
    const long commandTimeoutInMillis;
    CFRunLoopRef threadLoop;
    CFRunLoopSourceRef messageSource;
};

#endif
