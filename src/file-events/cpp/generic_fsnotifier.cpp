#include <sstream>

#include "generic_fsnotifier.h"

inline string createMessage(const string& message, const u16string& path) {
    stringstream ss;
    ss << message;
    ss << ": ";
    ss << utf16ToUtf8String(path);
    return ss.str();
}

inline string createMessage(const string& message, int errorCode) {
    stringstream ss;
    ss << message;
    ss << ", error = ";
    ss << errorCode;
    return ss.str();
}

inline string createMessage(const string& message, const u16string& path, int errorCode) {
    stringstream ss;
    ss << message;
    ss << ", error = ";
    ss << errorCode;
    ss << ": ";
    ss << utf16ToUtf8String(path);
    return ss.str();
}

FileWatcherException::FileWatcherException(const string& message, const u16string& path, int errorCode)
    : runtime_error(createMessage(message, path, errorCode)) {
}

FileWatcherException::FileWatcherException(const string& message, const u16string& path)
    : runtime_error(createMessage(message, path)) {
}

FileWatcherException::FileWatcherException(const string& message, int errorCode)
    : runtime_error(createMessage(message, errorCode)) {
}

FileWatcherException::FileWatcherException(const string& message)
    : runtime_error(message) {
}

JavaExceptionThrownException::JavaExceptionThrownException()
    : runtime_error("Java exception thrown from native code") {
}

InsufficientResourcesFileWatcherException::InsufficientResourcesFileWatcherException(const string& message)
    : FileWatcherException(message) {
}

AbstractServer::AbstractServer(JNIEnv* env, jobject watcherCallback)
    : JniSupport(env)
    , watcherCallback(env, watcherCallback) {
    jclass callbackClass = env->GetObjectClass(watcherCallback);
    this->watcherReportChangeEventMethod = env->GetMethodID(callbackClass, "reportChangeEvent", "(ILjava/lang/String;)V");
    this->watcherReportUnknownEventMethod = env->GetMethodID(callbackClass, "reportUnknownEvent", "(Ljava/lang/String;)V");
    this->watcherReportOverflowMethod = env->GetMethodID(callbackClass, "reportOverflow", "(Ljava/lang/String;)V");
    this->watcherReportFailureMethod = env->GetMethodID(callbackClass, "reportFailure", "(Ljava/lang/Throwable;)V");
    this->watcherReportTerminationMethod = env->GetMethodID(callbackClass, "reportTermination", "()V");
}

AbstractServer::~AbstractServer() {
}

void AbstractServer::reportChangeEvent(JNIEnv* env, ChangeType type, const u16string& path) {
    jstring javaPath = env->NewString((jchar*) path.c_str(), (jsize) path.length());
    env->CallVoidMethod(watcherCallback.get(), watcherReportChangeEventMethod, type, javaPath);
    env->DeleteLocalRef(javaPath);
    getJavaExceptionAndPrintStacktrace(env);
}

void AbstractServer::reportUnknownEvent(JNIEnv* env, const u16string& path) {
    jstring javaPath = env->NewString((jchar*) path.c_str(), (jsize) path.length());
    env->CallVoidMethod(watcherCallback.get(), watcherReportUnknownEventMethod, javaPath);
    env->DeleteLocalRef(javaPath);
    getJavaExceptionAndPrintStacktrace(env);
}

void AbstractServer::reportOverflow(JNIEnv* env, const u16string& path) {
    logToJava(LogLevel::INFO, "Detected overflow for %s", utf16ToUtf8String(path).c_str());
    jstring javaPath = env->NewString((jchar*) path.c_str(), (jsize) path.length());
    env->CallVoidMethod(watcherCallback.get(), watcherReportOverflowMethod, javaPath);
    env->DeleteLocalRef(javaPath);
    getJavaExceptionAndPrintStacktrace(env);
}

void AbstractServer::reportFailure(JNIEnv* env, const exception& exception) {
    u16string message = utf8ToUtf16String(exception.what());
    jstring javaMessage = env->NewString((jchar*) message.c_str(), (jsize) message.length());
    jmethodID constructor = env->GetMethodID(nativePlatformJniConstants->nativeExceptionClass.get(), "<init>", "(Ljava/lang/String;)V");
    jobject javaException = env->NewObject(nativePlatformJniConstants->nativeExceptionClass.get(), constructor, javaMessage);
    env->CallVoidMethod(watcherCallback.get(), watcherReportFailureMethod, javaException);
    env->DeleteLocalRef(javaMessage);
    env->DeleteLocalRef(javaException);
    getJavaExceptionAndPrintStacktrace(env);
}

void AbstractServer::reportTermination(JNIEnv* env) {
    env->CallVoidMethod(watcherCallback.get(), watcherReportTerminationMethod);
    getJavaExceptionAndPrintStacktrace(env);
}

AbstractServer* getServer(JNIEnv* env, jobject javaServer) {
    AbstractServer* server = (AbstractServer*) env->GetDirectBufferAddress(javaServer);
    if (server == NULL) {
        throw FileWatcherException("Closed already");
    }
    return server;
}

jobject rethrowAsJavaException(JNIEnv* env, const exception& e) {
    logToJava(LogLevel::SEVERE, "Caught exception: %s", e.what());
    return rethrowAsJavaException(env, e, nativePlatformJniConstants->nativeExceptionClass.get());
}

jobject rethrowAsJavaException(JNIEnv* env, const exception& e, jclass exceptionClass) {
    jint ret = env->ThrowNew(exceptionClass, e.what());
    if (ret != 0) {
        cerr << "JNI ThrowNew returned %d when rethrowing native exception: " << ret << endl;
    }
    return NULL;
}

jobject wrapServer(JNIEnv* env, AbstractServer* server) {
    return env->NewDirectByteBuffer(server, sizeof(server));
}

void AbstractServer::executeRunLoop(JNIEnv* env) {
    try {
        runLoop();
    } catch (const exception& ex) {
        rethrowAsJavaException(env, ex);
    }
    unique_lock<mutex> terminationLock(terminationMutex);
    terminated = true;
    reportTermination(env);
    terminationVariable.notify_all();
}

bool AbstractServer::awaitTermination(long timeoutInMillis) {
    unique_lock<mutex> terminationLock(terminationMutex);
    if (terminated) {
        return true;
    }
    auto status = terminationVariable.wait_for(terminationLock, chrono::milliseconds(timeoutInMillis));
    bool success = status != cv_status::timeout;
    return success;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions_00024NativeFileWatcher_initializeRunLoop0(JNIEnv* env, jobject, jobject javaServer) {
    try {
        AbstractServer* server = getServer(env, javaServer);
        server->initializeRunLoop();
    } catch (const exception& e) {
        rethrowAsJavaException(env, e);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions_00024NativeFileWatcher_executeRunLoop0(JNIEnv* env, jobject, jobject javaServer) {
    try {
        AbstractServer* server = getServer(env, javaServer);
        server->executeRunLoop(env);
    } catch (const exception& e) {
        rethrowAsJavaException(env, e);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions_00024NativeFileWatcher_startWatching0(JNIEnv* env, jobject, jobject javaServer, jobjectArray javaPaths) {
    try {
        AbstractServer* server = getServer(env, javaServer);
        vector<u16string> paths;
        javaToUtf16StringArray(env, javaPaths, paths);
        server->registerPaths(paths);
    } catch (const JavaExceptionThrownException&) {
        // Ignore, the Java exception has already been thrown.
    } catch (const exception& e) {
        rethrowAsJavaException(env, e);
    }
}

JNIEXPORT jboolean JNICALL
Java_net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions_00024NativeFileWatcher_stopWatching0(JNIEnv* env, jobject, jobject javaServer, jobjectArray javaPaths) {
    try {
        AbstractServer* server = getServer(env, javaServer);
        vector<u16string> paths;
        javaToUtf16StringArray(env, javaPaths, paths);
        return server->unregisterPaths(paths);
    } catch (const exception& e) {
        rethrowAsJavaException(env, e);
        return false;
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions_00024NativeFileWatcher_shutdown0(JNIEnv* env, jobject, jobject javaServer) {
    try {
        AbstractServer* server = getServer(env, javaServer);
        server->shutdownRunLoop();
    } catch (const exception& e) {
        rethrowAsJavaException(env, e);
    }
}

JNIEXPORT jboolean JNICALL
Java_net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions_00024NativeFileWatcher_awaitTermination0(JNIEnv* env, jobject, jobject javaServer, jlong timeoutInMillis) {
    try {
        AbstractServer* server = getServer(env, javaServer);
        bool successful = server->awaitTermination((long) timeoutInMillis);
        if (successful) {
            delete server;
        }
        return successful;
    } catch (const exception& e) {
        rethrowAsJavaException(env, e);
        return false;
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions_invalidateLogLevelCache0(JNIEnv* env, jobject) {
    try {
        logging->invalidateLogLevelCache();
    } catch (const exception& e) {
        rethrowAsJavaException(env, e);
    }
}

NativePlatformJniConstants::NativePlatformJniConstants(JavaVM* jvm)
    : JniSupport(jvm)
    , nativeExceptionClass(getThreadEnv(), "net/rubygrapefruit/platform/NativeException") {
}
