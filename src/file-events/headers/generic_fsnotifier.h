#pragma once

#include "generic.h"
#include "logging.h"
#include <functional>
#include <mutex>
#include <thread>

using namespace std;

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

class AbstractServer {
public:
    AbstractServer(JNIEnv* env, jobject watcherCallback);
    virtual ~AbstractServer();

    JNIEnv* getThreadEnv();

protected:
    void reportChange(JNIEnv* env, int type, const u16string& path);

    void startThread();
    virtual void runLoop(JNIEnv* env, function<void()> notifyStarted) = 0;

    thread watcherThread;

private:
    void run();
    mutex watcherThreadMutex;
    condition_variable watcherThreadStarted;

    jobject watcherCallback;
    jmethodID watcherCallbackMethod;

    JavaVM* jvm;
};
