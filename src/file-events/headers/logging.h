#pragma once

#include <chrono>
#include <jni.h>

#include "jni_support.h"

#define LOG_LEVEL_CHECK_INTERVAL_IN_MS 1000

enum class LogLevel : int {
    ALL,
    FINEST,
    FINER,
    FINE,
    CONFIG,
    INFO,
    WARNING,
    SEVERE,
    OFF
};

class Logging : public JniSupport {
public:
    Logging(JavaVM* jvm);

    void invalidateLogLevelCache();
    bool enabled(LogLevel level);
    void send(LogLevel level, const char* fmt, ...);

private:
    int minimumLogLevel;
    const JClass clsLogger;
    const jmethodID logMethod;
    const jmethodID getLevelMethod;
    chrono::time_point<chrono::steady_clock> lastLevelCheck;
};

extern Logging* logging;

#define logToJava(level, message, ...) (logging->enabled(level) ? logging->send(level, message, __VA_ARGS__) : ((void) NULL))
