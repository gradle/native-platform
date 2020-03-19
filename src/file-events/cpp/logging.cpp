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
    printlog(env, LOG_CONFIG, "Initialized logging to level %d\n", level);
}

void Logging::printlog(JNIEnv* env, int level, const char* fmt, ...) {
    if (minimumLogLevel > level) {
        return;
    }

    char buffer[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);

    if (env == NULL) {
        fprintf(stderr, "%s\n", buffer);
    } else {
        jstring logString = env->NewStringUTF(buffer);
        env->CallStaticVoidMethod(clsLogger, logMethod, level, logString);
        env->DeleteLocalRef(logString);
    }
}
