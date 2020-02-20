#include "logging.h"

int minimumLogLevel;
jclass clsLogger;
jmethodID logMethod;

JNIEXPORT void JNICALL Java_net_rubygrapefruit_platform_internal_jni_NativeLogger_initLogging(JNIEnv* env, jclass target, jint level) {
    minimumLogLevel = (int) level;
    clsLogger = env->FindClass("net/rubygrapefruit/platform/internal/jni/NativeLogger");
    logMethod = env->GetStaticMethodID(clsLogger, "log", "(ILjava/lang/String;)V");
    printlog(env, LOG_CONFIG, "Initialized logging to level %d\n", level);
}

void printlog(JNIEnv* env, int level, const char* fmt, ...) {
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
