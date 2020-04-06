#pragma once

#include <chrono>
#include <condition_variable>
#include <exception>
#include <functional>
#include <iostream>
#include <memory>
#include <mutex>
#include <queue>
#include <string>
#include <thread>
#include <vector>

#include "jni_support.h"
#include "logging.h"
#include "net_rubygrapefruit_platform_internal_jni_fileevents_AbstractFileEventFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_fileevents_NativeFileWatcher.h"

using namespace std;

// Corresponds to values of FileWatcherCallback.Type
#define FILE_EVENT_CREATED 0
#define FILE_EVENT_REMOVED 1
#define FILE_EVENT_MODIFIED 2
#define FILE_EVENT_INVALIDATE 3
#define FILE_EVENT_UNKNOWN 4

#define IS_SET(flags, flag) (((flags) & (flag)) == (flag))
#define IS_ANY_SET(flags, mask) (((flags) & (mask)) != 0)

#define THREAD_TIMEOUT (chrono::seconds(5))

struct FileWatcherException : public runtime_error {
public:
    FileWatcherException(const string& message, const u16string& path, int errorCode);
    FileWatcherException(const string& message, const u16string& path);
    FileWatcherException(const string& message, int errorCode);
    FileWatcherException(const string& message);
};

enum WatchPointStatus {
    /**
     * The watch point has been constructed, but not currently listening.
     */
    NOT_LISTENING,

    /**
     * The watch point is listening, expect events to arrive.
     */
    LISTENING,

    /**
     * The watch point has been cancelled, expect ERROR_OPERATION_ABORTED event.
     */
    CANCELLED,

    /**
     * The watch point has been cancelled, the ERROR_OPERATION_ABORTED event arrived; or starting the listener caused an error.
     */
    FINISHED
};

class AbstractServer;

class Command {
public:
    Command() {};
    virtual ~Command() {};

    void execute(AbstractServer* server) {
        try {
            success = perform(server);
        } catch (const exception&) {
            failure = current_exception();
        }
        executed.notify_all();
    }

    virtual bool perform(AbstractServer* server) = 0;

    condition_variable executed;
    bool success;
    exception_ptr failure;
};

class AbstractServer : public JniSupport {
public:
    AbstractServer(JNIEnv* env, jobject watcherCallback);
    virtual ~AbstractServer();

    /**
     * Execute command on processing thread sybnchronously.
     *
     * Returns wether the exeuction of the command had an effect.
     */
    bool executeOnThread(shared_ptr<Command> command);

    //
    // Methods running on the processing thread
    //

    /**
     * Processes queued commands, should be called from processing thread.
     */
    void processCommands();

    /**
     * Registers new watch point with the server for the given paths.
     * Runs on processing thread.
     */
    void registerPaths(const vector<u16string>& paths);

    /**
     * Unregisters watch points with the server for the given paths.
     * Runs on processing thread.
     */
    bool unregisterPaths(const vector<u16string>& paths);

    /**
     * Terminates server.
     * Runs on processing thread.
     */
    virtual void terminate() = 0;

protected:
    virtual void registerPath(const u16string& path) = 0;
    virtual bool unregisterPath(const u16string& path) = 0;

    void reportChange(JNIEnv* env, int type, const u16string& path);
    void reportError(JNIEnv* env, const exception& ex);

    void startThread();
    virtual void runLoop(function<void(exception_ptr)> notifyStarted) = 0;
    virtual void processCommandsOnThread() = 0;

    thread watcherThread;

private:
    void run();

    mutex watcherThreadMutex;
    condition_variable watcherThreadStarted;
    exception_ptr initException;

    mutex mtxCommands;
    deque<shared_ptr<Command>> commands;

    JniGlobalRef<jobject> watcherCallback;
    jmethodID watcherCallbackMethod;
    jmethodID watcherReportErrorMethod;
};

class RegisterPathsCommand : public Command {
public:
    RegisterPathsCommand(const vector<u16string>& paths)
        : paths(paths) {
    }

    bool perform(AbstractServer* server) override {
        server->registerPaths(paths);
        return true;
    }

private:
    const vector<u16string> paths;
};

class UnregisterPathsCommand : public Command {
public:
    UnregisterPathsCommand(const vector<u16string>& paths)
        : paths(paths) {
    }

    bool perform(AbstractServer* server) override {
        return server->unregisterPaths(paths);
    }

private:
    const vector<u16string> paths;
};

class TerminateCommand : public Command {
public:
    bool perform(AbstractServer* server) override {
        server->terminate();
        return true;
    }
};

class NativePlatformJniConstants : public JniSupport {
public:
    NativePlatformJniConstants(JavaVM* jvm);

    const JClass nativeExceptionClass;
    const JClass nativeFileWatcherClass;
};

extern NativePlatformJniConstants* nativePlatformJniConstants;

// TODO Use a template for the server type?
jobject wrapServer(JNIEnv* env, function<void*()> serverStarter);
