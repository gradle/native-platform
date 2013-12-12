/*
 * Copyright 2012 Adam Murdoch
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

/*
 * Curses functions
 */
#ifndef WIN32

#include "native.h"
#include "generic.h"
#include <unistd.h>
#include <stdlib.h>
#include <curses.h>
#include <term.h>

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
    return tgetstr((char*)capability, NULL);
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

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_getVersion(JNIEnv *env, jclass target) {
    return NATIVE_VERSION;
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
        jstring jtermType = char_to_java(env, termType, result);
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
