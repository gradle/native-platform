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
#include <langinfo.h>
#include <sys/utsname.h>
#include <xlocale.h>
#include <locale.h>

/*
 * Marks the given result as failed, using the current value of errno
 */
void mark_failed_with_errno(JNIEnv *env, const char* message, jobject result) {
    mark_failed_with_code(env, message, errno, result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_NativeLibraryFunctions_getSystemInfo(JNIEnv *env, jclass target, jobject info, jobject result) {
    jclass infoClass = env->GetObjectClass(info);

    // Empty string means load locale from environment.
    locale_t locale = newlocale(LC_CTYPE_MASK, "", NULL);
    if (locale == NULL) {
        mark_failed_with_message(env, "could not create locale", result);
        return;
    }

    jfieldID characterEncodingField = env->GetFieldID(infoClass, "characterEncoding", "Ljava/lang/String;");
    env->SetObjectField(info, characterEncodingField, env->NewStringUTF(nl_langinfo_l(CODESET, locale)));
    freelocale(locale);

    struct utsname machine_info;
    if (uname(&machine_info) != 0) {
        mark_failed_with_errno(env, "could not query machine details", result);
        return;
    }

    jfieldID osNameField = env->GetFieldID(infoClass, "osName", "Ljava/lang/String;");
    env->SetObjectField(info, osNameField, env->NewStringUTF(machine_info.sysname));
    jfieldID osVersionField = env->GetFieldID(infoClass, "osVersion", "Ljava/lang/String;");
    env->SetObjectField(info, osVersionField, env->NewStringUTF(machine_info.release));
    jfieldID machineArchitectureField = env->GetFieldID(infoClass, "machineArchitecture", "Ljava/lang/String;");
    env->SetObjectField(info, machineArchitectureField, env->NewStringUTF(machine_info.machine));
}

/*
 * File functions
 */

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_chmod(JNIEnv *env, jclass target, jbyteArray path, jint mode, jobject result) {
    jbyte* pathUtf8 = env->GetByteArrayElements(path, NULL);
    int retval = chmod((const char*)pathUtf8, mode);
    env->ReleaseByteArrayElements(path, pathUtf8, JNI_ABORT);
    if (retval != 0) {
        mark_failed_with_errno(env, "could not chmod file", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_stat(JNIEnv *env, jclass target, jbyteArray path, jobject dest, jobject result) {
    struct stat fileInfo;
    jbyte* pathUtf8 = env->GetByteArrayElements(path, NULL);
    int retval = stat((const char*)pathUtf8, &fileInfo);
    env->ReleaseByteArrayElements(path, pathUtf8, JNI_ABORT);
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
    struct stat fileInfo;
    int result;
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

#define NORMAL_TEXT 0
#define BRIGHT_TEXT 1
#define FOREGROUND_COLOR 2
#define CURSOR_UP 3
#define CURSOR_DOWN 4
#define CURSOR_LEFT 5
#define CURSOR_RIGHT 6
#define CURSOR_START_LINE 7
#define CLEAR_END_OF_LINE 8

#ifdef SOLARIS
#define TERMINAL_CHAR_TYPE char
#else
#define TERMINAL_CHAR_TYPE int
#endif

int current_terminal = -1;
const char* terminal_capabilities[9];

int write_to_terminal(TERMINAL_CHAR_TYPE ch) {
    write(current_terminal, &ch, 1);
}

const char* getcap(const char* capability) {
    char* cap = tgetstr((char*)capability, NULL);
//    if (cap == NULL) {
//        printf("unknown capability '%s'\n", capability);
//    }
    return cap;
}

void write_capability(JNIEnv *env, const char* capability, jobject result) {
    if (capability == NULL) {
        mark_failed_with_message(env, "unknown terminal capability", result);
        return;
    }
    if (tputs((char*)capability, 1, write_to_terminal) == ERR) {
        mark_failed_with_message(env, "could not write to terminal", result);
        return;
    }
}

void write_param_capability(JNIEnv *env, const char* capability, int count, jobject result) {
    if (capability == NULL) {
        mark_failed_with_message(env, "unknown terminal capability", result);
        return;
    }

    capability = tparm((char*)capability, count, 0, 0, 0, 0, 0, 0, 0, 0);
    if (capability == NULL) {
        mark_failed_with_message(env, "could not format terminal capability string", result);
        return;
    }

    if (tputs((char*)capability, 1, write_to_terminal) == ERR) {
        mark_failed_with_message(env, "could not write to terminal", result);
        return;
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_initTerminal(JNIEnv *env, jclass target, jint output, jobject capabilities, jobject result) {
    if (!isatty(output+1)) {
        mark_failed_with_message(env, "not a terminal", result);
        return;
    }
    if (current_terminal < 0) {
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

        jclass destClass = env->GetObjectClass(capabilities);
        jfieldID field = env->GetFieldID(destClass, "terminalName", "Ljava/lang/String;");
        jstring jtermType = env->NewStringUTF(termType);
        env->SetObjectField(capabilities, field, jtermType);

        // Text attributes
        terminal_capabilities[NORMAL_TEXT] = getcap("me");
        terminal_capabilities[BRIGHT_TEXT] = getcap("md");
        field = env->GetFieldID(destClass, "textAttributes", "Z");
        env->SetBooleanField(capabilities, field, terminal_capabilities[NORMAL_TEXT] != NULL && terminal_capabilities[BRIGHT_TEXT] != NULL);

        // Colors
        terminal_capabilities[FOREGROUND_COLOR] = getcap("AF");
        field = env->GetFieldID(destClass, "colors", "Z");
        env->SetBooleanField(capabilities, field, terminal_capabilities[FOREGROUND_COLOR] != NULL);

        // Cursor motion
        terminal_capabilities[CURSOR_UP] = getcap("up");
        terminal_capabilities[CURSOR_DOWN] = getcap("do");
        terminal_capabilities[CURSOR_LEFT] = getcap("le");
        terminal_capabilities[CURSOR_RIGHT] = getcap("nd");
        terminal_capabilities[CURSOR_START_LINE] = getcap("cr");
        terminal_capabilities[CLEAR_END_OF_LINE] = getcap("ce");
        field = env->GetFieldID(destClass, "cursorMotion", "Z");
        env->SetBooleanField(capabilities, field, terminal_capabilities[CURSOR_UP] != NULL
                                && terminal_capabilities[CURSOR_DOWN] != NULL
                                && terminal_capabilities[CURSOR_RIGHT] != NULL
                                && terminal_capabilities[CURSOR_LEFT] != NULL
                                && terminal_capabilities[CURSOR_START_LINE] != NULL
                                && terminal_capabilities[CLEAR_END_OF_LINE] != NULL);
    }
    current_terminal = output + 1;
    if (terminal_capabilities[NORMAL_TEXT] != NULL) {
        write_capability(env, terminal_capabilities[NORMAL_TEXT], result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_bold(JNIEnv *env, jclass target, jobject result) {
    write_capability(env, terminal_capabilities[BRIGHT_TEXT], result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_reset(JNIEnv *env, jclass target, jobject result) {
    if (terminal_capabilities[NORMAL_TEXT] != NULL) {
        write_capability(env, terminal_capabilities[NORMAL_TEXT], result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_foreground(JNIEnv *env, jclass target, jint color, jobject result) {
    write_param_capability(env, terminal_capabilities[FOREGROUND_COLOR], color, result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_up(JNIEnv *env, jclass target, jint count, jobject result) {
    for (jint i = 0; i < count; i++) {
        write_capability(env, terminal_capabilities[CURSOR_UP], result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_down(JNIEnv *env, jclass target, jint count, jobject result) {
    for (jint i = 0; i < count; i++) {
        write_capability(env, terminal_capabilities[CURSOR_DOWN], result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_left(JNIEnv *env, jclass target, jint count, jobject result) {
    for (jint i = 0; i < count; i++) {
        write_capability(env, terminal_capabilities[CURSOR_LEFT], result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_right(JNIEnv *env, jclass target, jint count, jobject result) {
    for (jint i = 0; i < count; i++) {
        write_capability(env, terminal_capabilities[CURSOR_RIGHT], result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_startLine(JNIEnv *env, jclass target, jobject result) {
    write_capability(env, terminal_capabilities[CURSOR_START_LINE], result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_clearToEndOfLine(JNIEnv *env, jclass target, jobject result) {
    write_capability(env, terminal_capabilities[CLEAR_END_OF_LINE], result);
}

#endif
