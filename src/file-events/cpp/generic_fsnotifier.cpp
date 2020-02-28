#include <assert.h>

#include "generic_fsnotifier.h"

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
            throw FileWatcherException("Failed to attach JNI to current thread");
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

AbstractServer::AbstractServer(JNIEnv* env, jobject watcherCallback) {
    JavaVM* jvm;
    int jvmStatus = env->GetJavaVM(&jvm);
    if (jvmStatus < 0) {
        throw FileWatcherException("Could not store jvm instance");
    }
    this->jvm = jvm;

    jclass callbackClass = env->GetObjectClass(watcherCallback);
    this->watcherCallbackMethod = env->GetMethodID(callbackClass, "pathChanged", "(ILjava/lang/String;)V");

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

    runLoop(env, [this](exception_ptr initException) {
        unique_lock<mutex> lock(watcherThreadMutex);
        this->initException = initException;
        watcherThreadStarted.notify_all();
        log_fine(getThreadEnv(), "Started thread", NULL);
    });

    log_fine(env, "Stopping thread", NULL);
}

void AbstractServer::executeOnThread(shared_ptr<Command> command) {
    unique_lock<mutex> lock(mtxCommands);
    commands.push_back(command);
    wakeUpRunLoop();
    command->executed.wait(lock);
    if (command->except) {
        rethrow_exception(command->except);
    }
}

void AbstractServer::processCommands() {
    unique_lock<mutex> lock(mtxCommands);
    for (auto& command : commands) {
        command->execute(this);
    }
    commands.clear();
}

JNIEnv* AbstractServer::getThreadEnv() {
    JNIEnv* env;
    jint ret = jvm->GetEnv((void**) &(env), JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        fprintf(stderr, "Failed to get JNI env for current thread: %d\n", ret);
        throw FileWatcherException("Failed to get JNI env for current thread");
    }
    return env;
}

void AbstractServer::reportChange(JNIEnv* env, int type, const u16string& path) {
    jstring javaPath = env->NewString((jchar*) path.c_str(), (jsize) path.length());
    env->CallVoidMethod(watcherCallback, watcherCallbackMethod, type, javaPath);
    env->DeleteLocalRef(javaPath);
}

u16string javaToNativeString(JNIEnv* env, jstring javaString) {
    jsize length = env->GetStringLength(javaString);
    const jchar* javaChars = env->GetStringCritical(javaString, nullptr);
    if (javaChars == NULL) {
        throw FileWatcherException("Could not get Java string character");
    }
    u16string path((char16_t*) javaChars, length);
    env->ReleaseStringCritical(javaString, javaChars);
    return path;
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
    jclass exceptionClass = env->FindClass("net/rubygrapefruit/platform/NativeException");
    assert(exceptionClass != NULL);
    jint ret = env->ThrowNew(exceptionClass, e.what());
    assert(ret == 0);
    return NULL;
}

jobject wrapServer(JNIEnv* env, function<void*()> serverStarter) {
    void* server;
    try {
        server = serverStarter();
    } catch (const exception& e) {
        return rethrowAsJavaException(env, e);
    }

    jclass clsWatcher = env->FindClass("net/rubygrapefruit/platform/internal/jni/AbstractFileEventFunctions$NativeFileWatcher");
    assert(clsWatcher != NULL);
    jmethodID constructor = env->GetMethodID(clsWatcher, "<init>", "(Ljava/lang/Object;)V");
    assert(constructor != NULL);
    return env->NewObject(clsWatcher, constructor, env->NewDirectByteBuffer(server, sizeof(server)));
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions_00024NativeFileWatcher_startWatching(JNIEnv* env, jobject, jobject javaServer, jstring javaPath) {
    try {
        AbstractServer* server = getServer(env, javaServer);
        auto path = javaToNativeString(env, javaPath);
        server->executeOnThread(shared_ptr<Command>(new RegisterCommand(path)));
    } catch (const exception& e) {
        rethrowAsJavaException(env, e);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_AbstractFileEventFunctions_00024NativeFileWatcher_stopWatching(JNIEnv* env, jobject, jobject javaServer, jstring javaPath) {
    try {
        AbstractServer* server = getServer(env, javaServer);
        auto path = javaToNativeString(env, javaPath);
        server->executeOnThread(shared_ptr<Command>(new UnregisterCommand(path)));
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
