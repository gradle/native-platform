#pragma once

#ifdef __linux__

#include <poll.h>
#include <sys/eventfd.h>
#include <sys/inotify.h>
#include <unordered_map>
#include <unordered_set>

#include "generic_fsnotifier.h"
#include "net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions.h"

using namespace std;

class Server;

struct Inotify {
    Inotify();
    ~Inotify();

    const int fd;
};

struct TerminateEvent {
    TerminateEvent();
    ~TerminateEvent();

    void trigger() const;
    void consume() const;

    const int fd;
};

class WatchPoint {
public:
    WatchPoint(const u16string& path, const shared_ptr<Inotify> inotify, int watchDescriptor);

    bool cancel();

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
    ~Server();

protected:
    void initializeRunLoop() override;
    void runLoop() override;
    void registerPath(const u16string& path) override;
    bool unregisterPath(const u16string& path) override;
    void terminateRunLoop() override;

private:
    void processQueues(int timeout);
    void handleEvents();
    void handleEvent(JNIEnv* env, const inotify_event* event);

    unordered_map<u16string, WatchPoint> watchPoints;
    unordered_map<int, u16string> watchRoots;
    unordered_set<int> recentlyRemovedWatchPoints;
    const shared_ptr<Inotify> inotify;
    const TerminateEvent terminateEvent;
    bool terminated = false;
    vector<uint8_t> buffer;
};

#endif
