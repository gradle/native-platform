#include <iostream>

#include "logging.h"

Logging::Logging(JavaVM* jvm)
    : JniSupport(jvm)
    , clsLogger(getThreadEnv(), "net/rubygrapefruit/platform/internal/jni/NativeLogger")
    , logMethod(getThreadEnv()->GetStaticMethodID(clsLogger.get(), "log", "(ILjava/lang/String;)V"))
    , getLevelMethod(getThreadEnv()->GetStaticMethodID(clsLogger.get(), "getLogLevel", "()I")) {
}

void Logging::invalidateLogLevelCache() {
    lastLevelCheck = chrono::steady_clock::time_point();
}

bool Logging::enabled(LogLevel level) {
    auto current = chrono::steady_clock::now();
    auto elapsed = chrono::duration_cast<chrono::milliseconds>(current - lastLevelCheck).count();
    if (elapsed > LOG_LEVEL_CHECK_INTERVAL_IN_MS) {
        JNIEnv* env = getThreadEnv();
        minimumLogLevel = env->CallStaticIntMethod(clsLogger.get(), getLevelMethod);
        rethrowJavaException(env);
        lastLevelCheck = current;
    }
    return minimumLogLevel <= static_cast<int>(level);
}

void Logging::send(LogLevel level, const char* fmt, ...) {
    char buffer[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);

    JNIEnv* env = getThreadEnv();
    if (env == NULL) {
        cerr << buffer << endl;
    } else {
        jstring logString = env->NewStringUTF(buffer);
        env->CallStaticVoidMethod(clsLogger.get(), logMethod, level, logString);
        env->DeleteLocalRef(logString);
        rethrowJavaException(env);
    }
}
