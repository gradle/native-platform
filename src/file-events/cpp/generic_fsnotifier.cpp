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

AbstractServer::AbstractServer(JNIEnv* env, jobject watcherCallback)
    : JniSupport(env)
    , watcherCallback(env, watcherCallback) {
    jclass callbackClass = env->GetObjectClass(watcherCallback);
    this->watcherCallbackMethod = env->GetMethodID(callbackClass, "pathChanged", "(ILjava/lang/String;)V");
    this->watcherReportErrorMethod = env->GetMethodID(callbackClass, "reportError", "(Ljava/lang/Throwable;)V");
}

AbstractServer::~AbstractServer() {
}

void AbstractServer::reportChange(JNIEnv* env, int type, const u16string& path) {
    jstring javaPath = env->NewString((jchar*) path.c_str(), (jsize) path.length());
    env->CallVoidMethod(watcherCallback.get(), watcherCallbackMethod, type, javaPath);
    env->DeleteLocalRef(javaPath);
    rethrowJavaException(env);
}

void AbstractServer::reportError(JNIEnv* env, const exception& exception) {
    u16string message = utf8ToUtf16String(exception.what());
    jstring javaMessage = env->NewString((jchar*) message.c_str(), (jsize) message.length());
    jmethodID constructor = env->GetMethodID(nativePlatformJniConstants->nativeExceptionClass.get(), "<init>", "(Ljava/lang/String;)V");
    jobject javaException = env->NewObject(nativePlatformJniConstants->nativeExceptionClass.get(), constructor, javaMessage);
    env->CallVoidMethod(watcherCallback.get(), watcherReportErrorMethod, javaException);
    env->DeleteLocalRef(javaMessage);
    env->DeleteLocalRef(javaException);
    rethrowJavaException(env);
}

AbstractServer* getServer(JNIEnv* env, jobject javaServer) {
    AbstractServer* server = (AbstractServer*) env->GetDirectBufferAddress(javaServer);
    if (server == NULL) {
        throw FileWatcherException("Closed already");
    }
    return server;
}

jobject rethrowAsJavaException(JNIEnv* env, const exception& e) {
    logToJava(SEVERE, "Caught exception: %s", e.what());
    jint ret = env->ThrowNew(nativePlatformJniConstants->nativeExceptionClass.get(), e.what());
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
    terminated.notify_all();
}

// TODO Add checks for terminated state
void AbstractServer::registerPaths(const vector<u16string>& paths) {
    unique_lock<mutex> lock(mutationMutex);
    for (auto& path : paths) {
        registerPath(path);
    }
}

bool AbstractServer::unregisterPaths(const vector<u16string>& paths) {
    bool success = true;
    unique_lock<mutex> lock(mutationMutex);
    for (auto& path : paths) {
        success &= unregisterPath(path);
    }
    return success;
}

void AbstractServer::terminate() {
    unique_lock<mutex> terminationLock(terminationMutex);
    terminateRunLoop();
    // TODO Parametrize this
    auto status = terminated.wait_for(terminationLock, THREAD_TIMEOUT);
    if (status == cv_status::timeout) {
        throw FileWatcherException("Termination timed out");
    }
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
Java_net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions_00024NativeFileWatcher_close0(JNIEnv* env, jobject, jobject javaServer) {
    try {
        AbstractServer* server = getServer(env, javaServer);
        delete server;
    } catch (const exception& e) {
        rethrowAsJavaException(env, e);
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
