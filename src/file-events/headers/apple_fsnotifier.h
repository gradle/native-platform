#pragma once

#if defined(__APPLE__)

#include <CoreServices/CoreServices.h>
#include <queue>
#include <unordered_map>

#include "generic_fsnotifier.h"
#include "net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions.h"

using namespace std;

class Server;

enum class WatchPointState {
    /**
     * The watchpoint has been created recently, so it shouldn't receive historical events.
     */
    NEW,

    /**
     * The watchpoint can receive historical events.
     */
    HISTORICAL
};

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

    virtual void registerPaths(const vector<u16string>& paths) override;
    virtual bool unregisterPaths(const vector<u16string>& paths) override;

protected:
    void initializeRunLoop() override;
    void runLoop() override;
    virtual void queueOnRunLoop(Command* command) override;

    void shutdownRunLoop() override;

private:
    /**
     * Opens the FSEventStream if there's anything to watch.
     */
    void openEventStream();

    /**
     * Closes the FSEventStream if one is open, and ensures that all pending events are processed.
     */
    void closeEventStream();

    void handleCommands();
    friend void acceptTrigger(void* info);

    WatchPointState getWatchPointState(const u16string& path);

    void handleEvent(JNIEnv* env, const u16string& path, const FSEventStreamEventFlags flags);
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
    unordered_map<u16string, WatchPointState> watchPoints;
    FSEventStreamRef eventStream = nullptr;
    bool finishedProcessingHistoricalEvents;

    mutex commandMutex;
    queue<Command*> commands;
    const long commandTimeoutInMillis;
    CFRunLoopRef threadLoop;
    CFRunLoopSourceRef messageSource;
};

#endif
