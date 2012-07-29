#include "native.h"
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>

void markFailed(JNIEnv *env, jobject result) {
    jclass destClass = env->GetObjectClass(result);
    jfieldID errnoField = env->GetFieldID(destClass, "errno", "I");
    env->SetIntField(result, errnoField, errno);
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