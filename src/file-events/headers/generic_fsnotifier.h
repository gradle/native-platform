#pragma once

#include <condition_variable>
#include <exception>
#include <functional>
#include <list>
#include <memory>
#include <mutex>
#include <queue>
#include <string>
#include <thread>

#include "jni_support.h"
#include "logging.h"
#include "net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions_NativeFileWatcher.h"

using namespace std;

// Corresponds to values of FileWatcherCallback.Type
#define FILE_EVENT_CREATED 0
#define FILE_EVENT_REMOVED 1
#define FILE_EVENT_MODIFIED 2
#define FILE_EVENT_INVALIDATE 3
#define FILE_EVENT_UNKNOWN 4

#define IS_SET(flags, flag) (((flags) & (flag)) == (flag))
#define IS_ANY_SET(flags, mask) (((flags) & (mask)) != 0)

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
    Command(){};
    virtual ~Command(){};

    void execute(AbstractServer* server) {
        try {
            perform(server);
        } catch (const exception&) {
            failure = current_exception();
        }
        executed.notify_all();
    }

    virtual void perform(AbstractServer* server) = 0;

    condition_variable executed;
    exception_ptr failure;
};

class AbstractServer : public JniSupport {
public:
    AbstractServer(JNIEnv* env, jobject watcherCallback);
    virtual ~AbstractServer();

    /**
     * Execute command on processing thread.
     */
    void executeOnThread(shared_ptr<Command> command);

    //
    // Methods running on the processing thread
    //

    /**
     * Processes queued commands, should be called from processing thread.
     */
    void processCommands();

    /**
     * Registers a new watch point with the server.
     * Runs on processing thread.
     */
    virtual void registerPath(const u16string& path) = 0;

    /**
     * Unregisters a new watch point with the server.
     * Runs on processing thread.
     */
    virtual void unregisterPath(const u16string& path) = 0;

    /**
     * Terminates server.
     * Runs on processing thread.
     */
    virtual void terminate() = 0;

protected:
    void reportChange(JNIEnv* env, int type, const u16string& path);
    void reportError(JNIEnv* env, const exception& ex);

    void startThread();
    virtual void runLoop(JNIEnv* env, function<void(exception_ptr)> notifyStarted) = 0;
    virtual void processCommandsOnThread() = 0;

    thread watcherThread;

private:
    void run();

    mutex watcherThreadMutex;
    condition_variable watcherThreadStarted;
    exception_ptr initException;

    mutex mtxCommands;
    deque<shared_ptr<Command>> commands;

    jobject watcherCallback;
    jmethodID watcherCallbackMethod;
    jmethodID watcherReportErrorMethod;
};

class RegisterPathCommand : public Command {
public:
    RegisterPathCommand(const u16string& path)
        : path(path) {
    }

    void perform(AbstractServer* server) override {
        server->registerPath(path);
    }

private:
    u16string path;
};

class UnregisterPathCommand : public Command {
public:
    UnregisterPathCommand(const u16string& path)
        : path(path) {
    }

    void perform(AbstractServer* server) override {
        server->unregisterPath(path);
    }

private:
    u16string path;
};

class TerminateCommand : public Command {
public:
    void perform(AbstractServer* server) override {
        server->terminate();
    }
};

class JniConstants : public JniSupport {
public:
    JniConstants(JavaVM* jvm);
    ~JniConstants();

    const jclass nativeExceptionClass;
    const jclass classClass;
    const jclass nativeFileWatcherClass;

private:
    const jclass findClass(const char* className);
};

extern JniConstants* jniConstants;

string javaToUtf8String(JNIEnv* env, jstring javaString);

u16string javaToUtf16String(JNIEnv* env, jstring javaString);

u16string utf8ToUtf16String(const char* string);

string utf16ToUtf8String(const u16string& string);

// TODO Use a template for the server type?
jobject wrapServer(JNIEnv* env, function<void*()> serverStarter);
