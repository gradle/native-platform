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
#include "net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions.h"
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

class AbstractServer : public JniSupport {
public:
    AbstractServer(JNIEnv* env, jobject watcherCallback);
    virtual ~AbstractServer();

    virtual void initializeRunLoop() = 0;
    virtual void executeRunLoop() = 0;

    /**
     * Registers new watch point with the server for the given paths.
     */
    void registerPaths(const vector<u16string>& paths);

    /**
     * Unregisters watch points with the server for the given paths.
     */
    bool unregisterPaths(const vector<u16string>& paths);

    /**
     * Terminates server.
     */
    void terminate();

protected:
    virtual void registerPath(const u16string& path) = 0;
    virtual bool unregisterPath(const u16string& path) = 0;
    virtual void terminateRunLoop() = 0;

    void reportChange(JNIEnv* env, int type, const u16string& path);
    void reportError(JNIEnv* env, const exception& ex);

    mutex mutationMutex;

private:
    JniGlobalRef<jobject> watcherCallback;
    jmethodID watcherCallbackMethod;
    jmethodID watcherReportErrorMethod;
};

class NativePlatformJniConstants : public JniSupport {
public:
    NativePlatformJniConstants(JavaVM* jvm);

    const JClass nativeExceptionClass;
};

extern NativePlatformJniConstants* nativePlatformJniConstants;

jobject wrapServer(JNIEnv* env, AbstractServer* server);
