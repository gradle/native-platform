#pragma once

#include "net_rubygrapefruit_platform_internal_jni_NativeLogger.h"
#include <jni.h>

#define LOG_FINEST 0
#define LOG_FINER 1
#define LOG_FINE 2
#define LOG_CONFIG 3
#define LOG_INFO 4
#define LOG_WARNING 5
#define LOG_SEVERE 6

#define log_finest(env, message, ...) printlog(env, LOG_FINEST, message, __VA_ARGS__)
#define log_finer(env, message, ...) printlog(env, LOG_FINER, message, __VA_ARGS__)
#define log_fine(env, message, ...) printlog(env, LOG_FINE, message, __VA_ARGS__)
#define log_config(env, message, ...) printlog(env, LOG_CONFIG, message, __VA_ARGS__)
#define log_info(env, message, ...) printlog(env, LOG_INFO, message, __VA_ARGS__)
#define log_warning(env, message, ...) printlog(env, LOG_WARNING, message, __VA_ARGS__)
#define log_severe(env, message, ...) printlog(env, LOG_SEVERE, message, __VA_ARGS__)

void printlog(JNIEnv* env, int level, const char* message, ...);
