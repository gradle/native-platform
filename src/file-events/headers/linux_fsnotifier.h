#pragma once

#ifdef __linux__

#include <sys/inotify.h>
#include <unordered_map>

#include "generic_fsnotifier.h"
#include "net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions_WatcherImpl.h"

using namespace std;

class Server;

class WatchPoint {
public:
    WatchPoint(const u16string& path, int fdInotify);
    ~WatchPoint();

    const int watchDescriptor;

private:
    const int fdInotify;
};

class Server : public AbstractServer {
public:
    Server(JNIEnv* env, jobject watcherCallback);
    ~Server();

    void startWatching(const u16string& path);
    void stopWatching(const u16string& path);

    // TODO This should be private
    void handleEvents(
        size_t numEvents);

protected:
    void runLoop(JNIEnv* env, function<void(exception_ptr)> notifyStarted) override;

private:
    void handleEvent(JNIEnv* env, char* path, int flags);

    unordered_map<u16string, WatchPoint> watchPoints;
    unordered_map<int, u16string> watchDescriptors;
    const int fdInotify;
};

#endif
