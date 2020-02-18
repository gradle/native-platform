#include "generic.h"
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
    ~AbstractServer();

    JNIEnv* getThreadEnv();

protected:
    // TODO Make this take a native string and free up the local JNI ref
    void reportChange(JNIEnv* env, int type, jstring path);

    // TODO Make this private
    JavaVM* jvm;

    void startThread();
    virtual void initializeRunLoop() = 0;
    virtual void runLoop() = 0;

    // TODO Make these private
    thread watcherThread;
    mutex watcherThreadMutex;
    condition_variable watcherThreadStarted;

private:
    void run();

    jobject watcherCallback;
    jmethodID watcherCallbackMethod;
};
