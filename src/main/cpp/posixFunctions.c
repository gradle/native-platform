#include "native.h"
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>
#include <unistd.h>
#include <sys/ioctl.h>

void markFailed(JNIEnv *env, jobject result) {
    jclass destClass = env->GetObjectClass(result);
    jmethodID method = env->GetMethodID(destClass, "failed", "(I)V");
    env->CallVoidMethod(result, method, errno);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_PosixFileFunctions_chmod(JNIEnv *env, jclass target, jstring path, jint mode, jobject result) {
    const char* pathUtf8 = env->GetStringUTFChars(path, NULL);
    int retval = chmod(pathUtf8, mode);
    if (retval != 0) {
        markFailed(env, result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_PosixFileFunctions_stat(JNIEnv *env, jclass target, jstring path, jobject dest, jobject result) {
    struct stat fileInfo;
    const char* pathUtf8 = env->GetStringUTFChars(path, NULL);
    int retval = stat(pathUtf8, &fileInfo);
    if (retval != 0) {
        markFailed(env, result);
        return;
    }
    jclass destClass = env->GetObjectClass(dest);
    jfieldID modeField = env->GetFieldID(destClass, "mode", "I");
    env->SetIntField(dest, modeField, 0777 & fileInfo.st_mode);
}

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_PosixProcessFunctions_getPid(JNIEnv *env, jclass target) {
    return getpid();
}

JNIEXPORT jboolean JNICALL
Java_net_rubygrapefruit_platform_internal_PosixTerminalFunctions_isatty(JNIEnv *env, jclass target, jint output) {
    switch (output) {
    case 0:
    case 1:
        return isatty(output+1) ? JNI_TRUE : JNI_FALSE;
    default:
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_PosixTerminalFunctions_getTerminalSize(JNIEnv *env, jclass target, jint output, jobject dimension, jobject result) {
    struct winsize screen_size;
    int retval = ioctl(output+1, TIOCGWINSZ, &screen_size);
    if (retval != 0) {
        markFailed(env, result);
        return;
    }
    jclass dimensionClass = env->GetObjectClass(dimension);
    jfieldID widthField = env->GetFieldID(dimensionClass, "cols", "I");
    env->SetIntField(dimension, widthField, screen_size.ws_col);
    jfieldID heightField = env->GetFieldID(dimensionClass, "rows", "I");
    env->SetIntField(dimension, heightField, screen_size.ws_row);
}
