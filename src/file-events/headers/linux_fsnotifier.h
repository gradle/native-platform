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

class WatchPoint {
public:
    WatchPoint(const u16string& path, int fdInotify);
    ~WatchPoint();

    void close();

    const int watchDescriptor;

private:
    const int fdInotify;
};

class Server : public AbstractServer {
public:
    Server(JNIEnv* env, jobject watcherCallback);
    ~Server();

    void registerPath(const u16string& path) override;
    void unregisterPath(const u16string& path) override;

protected:
    void runLoop(JNIEnv* env, function<void(exception_ptr)> notifyStarted) override;
    void processCommandsOnThread() override;
    void terminate() override;

private:
    void handleEventsInBuffer(JNIEnv* env, const char* buffer, ssize_t bytesRead);
    void handleEvent(JNIEnv* env, const inotify_event* event);

    unordered_map<u16string, WatchPoint> watchPoints;
    unordered_map<int, u16string> watchRoots;
    const int fdInotify;
    const int fdProcessCommandsEvent;
    bool terminated = false;
};

#endif
