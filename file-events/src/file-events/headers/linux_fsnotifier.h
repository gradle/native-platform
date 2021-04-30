#pragma once

#ifdef __linux__

#include <poll.h>
#include <sys/eventfd.h>
#include <sys/inotify.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <unordered_map>

#include "command.h"
#include "generic_fsnotifier.h"
#include "net_rubygrapefruit_platform_internal_jni_LinuxFileEventFunctions.h"

using namespace std;

struct InotifyInstanceLimitTooLowException : public InsufficientResourcesFileWatcherException {
public:
    InotifyInstanceLimitTooLowException();
};

struct InotifyWatchesLimitTooLowException : public InsufficientResourcesFileWatcherException {
public:
    InotifyWatchesLimitTooLowException();
};

class Server;

struct Inotify {
    Inotify();
    ~Inotify();

    const int fd;
};

class CommandEvent {
public:
    CommandEvent()
        : fd(eventfd(0, 0)) {
        if (fd == -1) {
            throw FileWatcherException("Couldn't register event source", errno);
        }
    }
    ~CommandEvent() {
        close(fd);
    }

    void trigger(Command* command) {
        this->commandToExecute = command;
        const uint64_t increment = 1;
        write(fd, &increment, sizeof(increment));
    }

    void process() {
        uint64_t counter;
        ssize_t bytesRead = read(fd, &counter, sizeof(counter));
        if (bytesRead == -1) {
            throw FileWatcherException("Couldn't read from command event notifier", errno);
        }
        this->commandToExecute->executeInsideRunLoop();
        this->commandToExecute = nullptr;
    }

    const int fd;

private:
    Command* commandToExecute;
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
    Server(JNIEnv* env, long commandTimeoutInMillis, jobject watcherCallback);

    virtual void registerPaths(const vector<u16string>& paths) override;
    virtual bool unregisterPaths(const vector<u16string>& paths) override;

protected:
    void initializeRunLoop() override;
    void runLoop() override;
    void shutdownRunLoop() override;

private:
    bool executeOnRunLoop(function<bool()> command);
    void processQueues(int timeout);
    void handleEvents();
    void handleEvent(JNIEnv* env, const inotify_event* event);

    void registerPath(const u16string& path);
    bool unregisterPath(const u16string& path);

    recursive_mutex mutationMutex;
    unordered_map<u16string, WatchPoint> watchPoints;
    unordered_map<int, u16string> watchRoots;
    unordered_map<int, u16string> recentlyUnregisteredWatchRoots;
    const shared_ptr<Inotify> inotify;
    CommandEvent commandEvent;
    const long commandTimeoutInMillis;
    bool shouldTerminate = false;
    vector<uint8_t> buffer;
};

class LinuxJniConstants : public JniSupport {
public:
    LinuxJniConstants(JavaVM* jvm);

    const JClass inotifyWatchesLimitTooLowExceptionClass;
    const JClass inotifyInstanceLimitTooLowExceptionClass;
};

extern LinuxJniConstants* linuxJniConstants;

#endif
