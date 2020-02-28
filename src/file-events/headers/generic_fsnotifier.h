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

struct FileWatcherException : public exception {
public:
    FileWatcherException(const char* message) {
        this->message = message;
    }

    const char* what() const throw() {
        return message;
    }

private:
    const char* message;
};

class AbstractServer;

class Command {
public:
    Command(){};
    virtual ~Command(){};
    virtual void perform(AbstractServer* server) = 0;
};

class AbstractServer {
public:
    AbstractServer(JNIEnv* env, jobject watcherCallback);
    virtual ~AbstractServer();

    void executeOnThread(Command* command);
    void processCommands();

    virtual void startWatching(const u16string& path) = 0;
    virtual void stopWatching(const u16string& path) = 0;
    virtual void terminate() = 0;

    JNIEnv* getThreadEnv();

protected:
    void reportChange(JNIEnv* env, int type, const u16string& path);

    virtual void wakeUpRunLoop() = 0;

    void startThread();
    virtual void runLoop(JNIEnv* env, function<void(exception_ptr)> notifyStarted) = 0;

    thread watcherThread;

private:
    void run();
    mutex watcherThreadMutex;
    condition_variable watcherThreadStarted;
    exception_ptr initException;

    mutex mtxCommands;
    condition_variable commandsProcessed;
    deque<unique_ptr<Command>> commands;
    exception_ptr executionException;

    jobject watcherCallback;
    jmethodID watcherCallbackMethod;

    JavaVM* jvm;
};

class RegisterCommand : public Command {
public:
    RegisterCommand(const u16string& path)
        : path(path) {
    }

    void perform(AbstractServer* server) override {
        server->startWatching(path);
    }

private:
    u16string path;
};

class UnregisterCommand : public Command {
public:
    UnregisterCommand(const u16string& path)
        : path(path) {
    }

    void perform(AbstractServer* server) override {
        server->stopWatching(path);
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

u16string javaToNativeString(JNIEnv* env, jstring javaString);

// TODO Use a template for the server type?
jobject wrapServer(JNIEnv* env, function<void*()> serverStarter);
