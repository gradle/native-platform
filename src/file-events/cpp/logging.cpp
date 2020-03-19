#include "logging.h"

Logging* logging;

JNIEXPORT void JNICALL Java_net_rubygrapefruit_platform_internal_jni_NativeLogger_initLogging(JNIEnv* env, jclass, jint level) {
    logging = new Logging(env, (int) level);
}

Logging::Logging(JNIEnv* env, int level)
    : JniSupport(env)
    , minimumLogLevel(level)
    , clsLogger(findClass("net/rubygrapefruit/platform/internal/jni/NativeLogger"))
    , logMethod(env->GetStaticMethodID(clsLogger, "log", "(ILjava/lang/String;)V")) {
    send(LogLevel::CONFIG, "Initialized logging to level %d\n", level);
}
Logging::~Logging() {
    JNIEnv* env = getThreadEnv();
    env->DeleteGlobalRef(clsLogger);
}

inline bool Logging::enabled(LogLevel level) {
    return minimumLogLevel <= level;
}

void Logging::send(LogLevel level, const char* fmt, ...) {
    char buffer[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);

    JNIEnv* env = getThreadEnv();
    if (env == NULL) {
        fprintf(stderr, "%s\n", buffer);
    } else {
        jstring logString = env->NewStringUTF(buffer);
        env->CallStaticVoidMethod(clsLogger, logMethod, level, logString);
        env->DeleteLocalRef(logString);
    }
}
