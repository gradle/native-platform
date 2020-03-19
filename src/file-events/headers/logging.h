#pragma once

#include <jni.h>

#include "jni_support.h"
#include "net_rubygrapefruit_platform_internal_jni_NativeLogger.h"

enum LogLevel : int {
    FINEST = 0,
    FINER = 1,
    FINE = 2,
    CONFIG = 3,
    INFO = 4,
    WARNING = 5,
    SEVERE = 6
};

class Logging : public JniSupport {
public:
    Logging(JNIEnv* env, int level);
    ~Logging();

    bool enabled(LogLevel level);
    void send(LogLevel level, const char* fmt, ...);

private:
    int minimumLogLevel;
    const jclass clsLogger;
    const jmethodID logMethod;
};

extern Logging* logging;

#define log(level, message, ...) (logging->enabled(level) ? logging->send(level, message, __VA_ARGS__) : ((void) NULL))
