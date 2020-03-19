#include <codecvt>
#include <locale>
#include <sstream>
#include <string>

#include "generic_fsnotifier.h"

JniConstants* jniConstants;

class JNIThread {
public:
    JNIThread(JavaVM* jvm, const char* name, bool daemon) {
        this->jvm = jvm;

        JNIEnv* env;
        JavaVMAttachArgs args = {
            JNI_VERSION_1_6,            // version
            const_cast<char*>(name),    // thread name
            NULL                        // thread group
        };
        jint ret = daemon
            ? jvm->AttachCurrentThreadAsDaemon((void**) &env, (void*) &args)
            : jvm->AttachCurrentThread((void**) &env, (void*) &args);
        if (ret != JNI_OK) {
            fprintf(stderr, "Failed to attach JNI to current thread: %d\n", ret);
            throw FileWatcherException("Failed to attach JNI to current thread", ret);
        }
    }
    ~JNIThread() {
        jint ret = jvm->DetachCurrentThread();
        if (ret != JNI_OK) {
            fprintf(stderr, "Failed to detach JNI from current thread: %d\n", ret);
        }
    }

private:
    JavaVM* jvm;
};

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

AbstractServer::AbstractServer(JNIEnv* env, jobject watcherCallback) {
    JavaVM* jvm;
    int jvmStatus = env->GetJavaVM(&jvm);
    if (jvmStatus < 0) {
        throw FileWatcherException("Could not store jvm instance", jvmStatus);
    }
    this->jvm = jvm;

    jclass callbackClass = env->GetObjectClass(watcherCallback);
    this->watcherCallbackMethod = env->GetMethodID(callbackClass, "pathChanged", "(ILjava/lang/String;)V");
    this->watcherReportErrorMethod = env->GetMethodID(callbackClass, "reportError", "(Ljava/lang/Throwable;)V");

    jobject globalWatcherCallback = env->NewGlobalRef(watcherCallback);
    if (globalWatcherCallback == NULL) {
        throw FileWatcherException("Could not get global ref for watcher callback");
    }
    this->watcherCallback = globalWatcherCallback;
}

AbstractServer::~AbstractServer() {
    JNIEnv* env = getThreadEnv();
    if (env != NULL) {
        env->DeleteGlobalRef(watcherCallback);
    }
}

void AbstractServer::startThread() {
    unique_lock<mutex> lock(watcherThreadMutex);
    this->watcherThread = thread(&AbstractServer::run, this);
    this->watcherThreadStarted.wait(lock);
    if (initException) {
        if (watcherThread.joinable()) {
            watcherThread.join();
        }
        rethrow_exception(initException);
    }
}

void AbstractServer::run() {
    JNIThread jniThread(jvm, "File watcher server", true);
    JNIEnv* env = getThreadEnv();

    log_fine(env, "Starting thread", NULL);

    try {
        runLoop(env, [this](exception_ptr initException) {
            unique_lock<mutex> lock(watcherThreadMutex);
            this->initException = initException;
            watcherThreadStarted.notify_all();
            log_fine(getThreadEnv(), "Started thread", NULL);
        });
    } catch (const exception& ex) {
        reportError(env, ex);
    }

    log_fine(env, "Stopping thread", NULL);
}

void AbstractServer::executeOnThread(shared_ptr<Command> command) {
    unique_lock<mutex> lock(mtxCommands);
    commands.push_back(command);
    processCommandsOnThread();
    command->executed.wait(lock);
    if (command->failure) {
        rethrow_exception(command->failure);
    }
}

void AbstractServer::processCommands() {
    unique_lock<mutex> lock(mtxCommands);
    for (auto& command : commands) {
        command->execute(this);
    }
    commands.clear();
}

static jint lookupEnv(JavaVM* jvm, JNIEnv** env) {
    jint ret = jvm->GetEnv((void**) env, JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        fprintf(stderr, "Failed to get JNI env for current thread: %d\n", ret);
    }
    return ret;
}

JNIEnv* AbstractServer::getThreadEnv() {
    JNIEnv* env;
    jint ret = lookupEnv(jvm, &env);
    if (ret != JNI_OK) {
        throw FileWatcherException("Failed to get JNI env for current thread", ret);
    }
    return env;
}

void AbstractServer::reportChange(JNIEnv* env, int type, const u16string& path) {
    jstring javaPath = env->NewString((jchar*) path.c_str(), (jsize) path.length());
    env->CallVoidMethod(watcherCallback, watcherCallbackMethod, type, javaPath);
    env->DeleteLocalRef(javaPath);

    jthrowable exception = env->ExceptionOccurred();
    if (exception != nullptr) {
        env->ExceptionDescribe();
        env->ExceptionClear();

        jclass exceptionClass = env->GetObjectClass(exception);
        jmethodID getMessage = env->GetMethodID(exceptionClass, "getMessage", "()Ljava/lang/String;");
        jstring javaMessage = (jstring) env->CallObjectMethod(exception, getMessage);
        string message = javaToUtf8String(env, javaMessage);
        env->DeleteLocalRef(javaMessage);

        jmethodID getClassName = env->GetMethodID(jniConstants->classClass, "getName", "()Ljava/lang/String;");
        jstring javaExceptionType = (jstring) env->CallObjectMethod(exceptionClass, getClassName);
        string exceptionType = javaToUtf8String(env, javaExceptionType);
        env->DeleteLocalRef(javaExceptionType);

        env->DeleteLocalRef(exceptionClass);
        env->DeleteLocalRef(exception);

        throw FileWatcherException("Caught " + exceptionType + " while calling callback: " + message);
    }
}

void AbstractServer::reportError(JNIEnv* env, const exception& exception) {
    u16string message = utf8ToUtf16String(exception.what());
    jstring javaMessage = env->NewString((jchar*) message.c_str(), (jsize) message.length());
    jmethodID constructor = env->GetMethodID(jniConstants->nativeExceptionClass, "<init>", "(Ljava/lang/String;)V");
    jobject javaException = env->NewObject(jniConstants->nativeExceptionClass, constructor, javaMessage);
    env->CallVoidMethod(watcherCallback, watcherReportErrorMethod, javaException);
    env->DeleteLocalRef(javaMessage);
    env->DeleteLocalRef(javaException);
}

string javaToUtf8String(JNIEnv* env, jstring javaString) {
    return utf16ToUtf8String(javaToUtf16String(env, javaString));
}

u16string javaToUtf16String(JNIEnv* env, jstring javaString) {
    jsize length = env->GetStringLength(javaString);
    const jchar* javaChars = env->GetStringCritical(javaString, nullptr);
    if (javaChars == NULL) {
        throw FileWatcherException("Could not get Java string character");
    }
    u16string path((char16_t*) javaChars, length);
    env->ReleaseStringCritical(javaString, javaChars);
    return path;
}

// Utility wrapper to adapt locale-bound facets for wstring convert
// Exposes the protected destructor as public
// See https://en.cppreference.com/w/cpp/locale/codecvt
template <class Facet>
struct deletable_facet : Facet {
    template <class... Args>
    deletable_facet(Args&&... args)
        : Facet(forward<Args>(args)...) {
    }
    ~deletable_facet() {
    }
};

u16string utf8ToUtf16String(const char* string) {
    wstring_convert<deletable_facet<codecvt<char16_t, char, mbstate_t>>, char16_t> conv16;
    return conv16.from_bytes(string);
}

string utf16ToUtf8String(const u16string& string) {
    wstring_convert<deletable_facet<codecvt<char16_t, char, mbstate_t>>, char16_t> conv16;
    return conv16.to_bytes(string);
}

AbstractServer* getServer(JNIEnv* env, jobject javaServer) {
    AbstractServer* server = (AbstractServer*) env->GetDirectBufferAddress(javaServer);
    if (server == NULL) {
        throw FileWatcherException("Closed already");
    }
    return server;
}

jobject rethrowAsJavaException(JNIEnv* env, const exception& e) {
    log_severe(env, "Caught exception: %s", e.what());
    jint ret = env->ThrowNew(jniConstants->nativeExceptionClass, e.what());
    if (ret != 0) {
        fprintf(stderr, "JNI ThrowNew returned %d when rethrowing native exception: %s\n", ret, e.what());
    }
    return NULL;
}

jobject wrapServer(JNIEnv* env, function<void*()> serverStarter) {
    void* server;
    try {
        server = serverStarter();
    } catch (const exception& e) {
        return rethrowAsJavaException(env, e);
    }

    jmethodID constructor = env->GetMethodID(jniConstants->nativeFileWatcherClass, "<init>", "(Ljava/lang/Object;)V");
    return env->NewObject(jniConstants->nativeFileWatcherClass, constructor, env->NewDirectByteBuffer(server, sizeof(server)));
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions_00024NativeFileWatcher_startWatching(JNIEnv* env, jobject, jobject javaServer, jstring javaPath) {
    try {
        AbstractServer* server = getServer(env, javaServer);
        auto path = javaToUtf16String(env, javaPath);
        server->executeOnThread(shared_ptr<Command>(new RegisterPathCommand(path)));
    } catch (const exception& e) {
        rethrowAsJavaException(env, e);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions_00024NativeFileWatcher_stopWatching(JNIEnv* env, jobject, jobject javaServer, jstring javaPath) {
    try {
        AbstractServer* server = getServer(env, javaServer);
        auto path = javaToUtf16String(env, javaPath);
        server->executeOnThread(shared_ptr<Command>(new UnregisterPathCommand(path)));
    } catch (const exception& e) {
        rethrowAsJavaException(env, e);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions_00024NativeFileWatcher_stop(JNIEnv* env, jobject, jobject javaServer) {
    try {
        AbstractServer* server = getServer(env, javaServer);
        delete server;
    } catch (const exception& e) {
        rethrowAsJavaException(env, e);
    }
}

static jclass findClass(JNIEnv* env, const char* className) {
    jclass localRef = env->FindClass(className);
    jclass globalRef = reinterpret_cast<jclass>(env->NewGlobalRef(localRef));
    env->DeleteLocalRef(localRef);
    return globalRef;
}

JniConstants::JniConstants(JNIEnv* env)
    : nativeExceptionClass(findClass(env, "net/rubygrapefruit/platform/NativeException"))
    , classClass(findClass(env, "java/lang/Class"))
    , nativeFileWatcherClass(findClass(env, "net/rubygrapefruit/platform/internal/jni/AbstractFileEventFunctions$NativeFileWatcher")) {
}

void JniConstants::unload(JNIEnv* env) {
    env->DeleteGlobalRef(nativeExceptionClass);
    env->DeleteGlobalRef(classClass);
    env->DeleteGlobalRef(nativeFileWatcherClass);
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* jvm, void*) {
    JNIEnv* env;
    if (lookupEnv(jvm, &env) != JNI_OK) {
        return -1;
    }
    jniConstants = new JniConstants(env);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM* jvm, void*) {
    JNIEnv* env;
    if (lookupEnv(jvm, &env) != JNI_OK) {
        return;
    }
    jniConstants->unload(env);
    delete jniConstants;
}
