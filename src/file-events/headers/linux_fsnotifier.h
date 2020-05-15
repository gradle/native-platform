#pragma once

#ifdef __linux__

#include <poll.h>
#include <sys/eventfd.h>
#include <sys/inotify.h>
#include <unordered_map>

#include "generic_fsnotifier.h"
#include "net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions.h"

using namespace std;

class Server;

struct Inotify {
    Inotify();
    ~Inotify();

    const int fd;
};

struct ShutdownEvent {
    ShutdownEvent();
    ~ShutdownEvent();

    void trigger() const;
    void consume() const;

    const int fd;
};

enum class WatchPointStatus {
    /**
     * The watch point is listening, expect events to arrive.
     */
    LISTENING,

    /**
     * The watch point has been cancelled, expect IN_IGNORED event.
     */
    CANCELLED
};

enum class CancelResult {
    /**
     * The watch point was successfully cancelled.
     */
    CANCELLED,

    /**
     * The watch point was not cancelled (probably because it was removed).
     */
    NOT_CANCELLED,

    /**
     * The watch poing has already been cancelled earlier.
     */
    ALREADY_CANCELLED
};

class WatchPoint {
public:
    WatchPoint(const u16string& path, const shared_ptr<Inotify> inotify, int watchDescriptor);

    CancelResult cancel();

private:
    WatchPointStatus status;
    const int watchDescriptor;
    const shared_ptr<Inotify> inotify;
    const u16string path;

    friend class Server;
};

class Server : public AbstractServer {
public:
    Server(JNIEnv* env, jobject watcherCallback);

protected:
    void initializeRunLoop() override;
    void runLoop() override;
    void registerPath(const u16string& path) override;
    bool unregisterPath(const u16string& path) override;
    void shutdownRunLoop() override;

private:
    void processQueues(int timeout);
    void handleEvents();
    void handleEvent(JNIEnv* env, const inotify_event* event);

    unordered_map<u16string, WatchPoint> watchPoints;
    unordered_map<int, u16string> watchRoots;
    unordered_map<int, u16string> recentlyUnregisteredWatchRoots;
    const shared_ptr<Inotify> inotify;
    const ShutdownEvent shutdownEvent;
    bool shouldTerminate = false;
    vector<uint8_t> buffer;
};

#endif
