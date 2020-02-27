#pragma once

#ifdef __linux__

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

    void startWatching(const u16string& path) override;
    void stopWatching(const u16string& path) override;

protected:
    void runLoop(JNIEnv* env, function<void(exception_ptr)> notifyStarted) override;

private:
    void handleEvent(JNIEnv* env, const inotify_event* event);

    unordered_map<u16string, WatchPoint> watchPoints;
    unordered_map<int, u16string> watchRoots;
    const int fdInotify;
    bool terminate = false;
};

#endif
