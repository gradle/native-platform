#ifndef WIN32

#include "native.h"
#include "generic.h"
#include <stdlib.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <curses.h>
#include <term.h>

/*
 * Marks the given result as failed, using the current value of errno
 */
void mark_failed_with_errno(JNIEnv *env, const char* message, jobject result) {
    mark_failed_with_code(env, message, errno, result);
}

/*
 * File functions
 */

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_chmod(JNIEnv *env, jclass target, jstring path, jint mode, jobject result) {
    const char* pathUtf8 = env->GetStringUTFChars(path, NULL);
    int retval = chmod(pathUtf8, mode);
    env->ReleaseStringUTFChars(path, pathUtf8);
    if (retval != 0) {
        mark_failed_with_errno(env, "could not chmod file", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_stat(JNIEnv *env, jclass target, jstring path, jobject dest, jobject result) {
    struct stat fileInfo;
    const char* pathUtf8 = env->GetStringUTFChars(path, NULL);
    int retval = stat(pathUtf8, &fileInfo);
    env->ReleaseStringUTFChars(path, pathUtf8);
    if (retval != 0) {
        mark_failed_with_errno(env, "could not stat file", result);
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
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getPid(JNIEnv *env, jclass target) {
    return getpid();
}

/*
 * Terminal functions
 */

JNIEXPORT jboolean JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixTerminalFunctions_isatty(JNIEnv *env, jclass target, jint output) {
    switch (output) {
    case 0:
    case 1:
        return isatty(output+1) ? JNI_TRUE : JNI_FALSE;
    default:
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixTerminalFunctions_getTerminalSize(JNIEnv *env, jclass target, jint output, jobject dimension, jobject result) {
    struct winsize screen_size;
    int retval = ioctl(output+1, TIOCGWINSZ, &screen_size);
    if (retval != 0) {
        mark_failed_with_errno(env, "could not fetch terminal size", result);
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
        mark_failed_with_message(env, "unknown terminal capability", result);
        return;
    }
    if (tputs(cap, 1, write_to_terminal) == ERR) {
        mark_failed_with_message(env, "could not write to terminal", result);
        return;
    }
}

void write_param_capability(JNIEnv *env, const char* capability, int count, jobject result) {
    char* cap = tgetstr((char*)capability, NULL);
    if (cap == NULL) {
        mark_failed_with_message(env, "unknown terminal capability", result);
        return;
    }

    cap = tparm(cap, count, 0, 0, 0, 0, 0, 0, 0, 0);
    if (cap == NULL) {
        mark_failed_with_message(env, "could not format terminal capability string", result);
        return;
    }

    if (tputs(cap, 1, write_to_terminal) == ERR) {
        mark_failed_with_message(env, "could not write to terminal", result);
        return;
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_initTerminal(JNIEnv *env, jclass target, jint output, jobject result) {
    if (!isatty(output+1)) {
        mark_failed_with_message(env, "not a terminal", result);
        return;
    }
    char* termType = getenv("TERM");
    if (termType == NULL) {
        mark_failed_with_message(env, "$TERM not set", result);
        return;
    }
    int retval = tgetent(NULL, termType);
    if (retval != 1) {
        mark_failed_with_message(env, "could not get termcap entry", result);
        return;
    }
    current_terminal = output + 1;
    write_capability(env, "me", result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_bold(JNIEnv *env, jclass target, jobject result) {
    write_capability(env, "md", result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_reset(JNIEnv *env, jclass target, jobject result) {
    write_capability(env, "me", result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_foreground(JNIEnv *env, jclass target, jint color, jobject result) {
    write_param_capability(env, "AF", color, result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_up(JNIEnv *env, jclass target, jint count, jobject result) {
    for (jint i = 0; i < count; i++) {
        write_capability(env, "up", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_down(JNIEnv *env, jclass target, jint count, jobject result) {
    for (jint i = 0; i < count; i++) {
        write_capability(env, "do", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_left(JNIEnv *env, jclass target, jint count, jobject result) {
    for (jint i = 0; i < count; i++) {
        write_capability(env, "le", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_right(JNIEnv *env, jclass target, jint count, jobject result) {
    for (jint i = 0; i < count; i++) {
        write_capability(env, "nd", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_startLine(JNIEnv *env, jclass target, jobject result) {
    write_capability(env, "cr", result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_clearToEndOfLine(JNIEnv *env, jclass target, jobject result) {
    write_capability(env, "ce", result);
}


#endif
