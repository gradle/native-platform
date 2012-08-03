#include "native.h"
#include <stdlib.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <curses.h>
#include <term.h>

void markFailed(JNIEnv *env, jobject result) {
    jclass destClass = env->GetObjectClass(result);
    jmethodID method = env->GetMethodID(destClass, "failed", "(I)V");
    env->CallVoidMethod(result, method, errno);
}

/*
 * Generic functions
 */

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_NativeLibraryFunctions_getVersion(JNIEnv *env, jclass target) {
    return 1;
}

/*
 * File functions
 */

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

/*
 * Process functions
 */

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_PosixProcessFunctions_getPid(JNIEnv *env, jclass target) {
    return getpid();
}

/*
 * Terminal functions
 */

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

/*
 * Terminfo functions
 */

int current_terminal = -1;

int write_to_terminal(int ch) {
    write(current_terminal, &ch, 1);
}

void write_capability(JNIEnv *env, const char* capability, jobject result) {
    char* cap = tgetstr((char*)capability, NULL);
    if (cap == NULL) {
        markFailed(env, result);
        return;
    }
    if (tputs(cap, 1, write_to_terminal) == ERR) {
        markFailed(env, result);
        return;
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_TerminfoFunctions_initTerminal(JNIEnv *env, jclass target, jint output, jobject result) {
    char* termType = getenv("TERM");
    if (termType == NULL) {
        markFailed(env, result);
        return;
    }
    int retval = tgetent(NULL, termType);
    if (retval != 1) {
        markFailed(env, result);
        return;
    }
    current_terminal = output + 1;
    write_capability(env, "me", result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_TerminfoFunctions_bold(JNIEnv *env, jclass target, jint output, jobject result) {
    write_capability(env, "md", result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_TerminfoFunctions_normal(JNIEnv *env, jclass target, jint output, jobject result) {
    write_capability(env, "me", result);
}
