#pragma once

#include <jni.h>

#include "jni_support.h"
#include "net_rubygrapefruit_platform_internal_jni_NativeLogger.h"

#define LOG_FINEST 0
#define LOG_FINER 1
#define LOG_FINE 2
#define LOG_CONFIG 3
#define LOG_INFO 4
#define LOG_WARNING 5
#define LOG_SEVERE 6

class Logging : public JniSupport {
public:
    Logging(JNIEnv* env, int level);
    ~Logging();

    void printlog(int level, const char* message, ...);

private:
    int minimumLogLevel;
    const jclass clsLogger;
    const jmethodID logMethod;
};

extern Logging* logging;

#define log_finest(message, ...) (logging->printlog(LOG_FINEST, message, __VA_ARGS__))
#define log_finer(message, ...) (logging->printlog(LOG_FINER, message, __VA_ARGS__)
#define log_fine(message, ...) (logging->printlog(LOG_FINE, message, __VA_ARGS__))
#define log_config(message, ...) (logging->printlog(LOG_CONFIG, message, __VA_ARGS__))
#define log_info(message, ...) (logging->printlog(LOG_INFO, message, __VA_ARGS__))
#define log_warning(message, ...) (logging->printlog(LOG_WARNING, message, __VA_ARGS__))
#define log_severe(message, ...) (logging->printlog(LOG_SEVERE, message, __VA_ARGS__))
