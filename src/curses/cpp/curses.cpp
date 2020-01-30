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
#ifndef _WIN32

#include "net_rubygrapefruit_platform_internal_jni_TerminfoFunctions.h"
#include "generic.h"
#include <unistd.h>
#include <stdlib.h>
#include <curses.h>
#include <term.h>

#ifdef SOLARIS
#define TERMINAL_CHAR_TYPE char
#else
#define TERMINAL_CHAR_TYPE int
#endif

const char* getcap(const char* capability) {
    return tgetstr((char*)capability, NULL);
}

#define BUFFER_LEN 20

int is_init = 0;
int buffer_pos = 0;
jbyte buffer[BUFFER_LEN];

int write_to_buffer(TERMINAL_CHAR_TYPE ch) {
    if (buffer_pos == BUFFER_LEN) {
        return EOF;
    }

    buffer[buffer_pos] = ch;
    buffer_pos++;

    return ch;
}

jbyteArray byte_array_for_capability(JNIEnv *env, const char* capability, jobject result) {
    buffer_pos = 0;
    if (tputs((char*)capability, 1, write_to_buffer) == ERR) {
        mark_failed_with_message(env, "could not write to buffer", result);
        return NULL;
    }
    jbyteArray bytes = env->NewByteArray(buffer_pos);
    env->SetByteArrayRegion(bytes, 0, buffer_pos, buffer);
    return bytes;
}

jbyteArray read_capability(JNIEnv *env, const char* capability, jobject result) {
    if (capability == NULL) {
        return NULL;
    }
    return byte_array_for_capability(env, capability, result);
}

jbyteArray read_param_capability(JNIEnv *env, const char* capability, int count, jobject result) {
    if (capability == NULL) {
        return NULL;
    }

    capability = tparm((char*)capability, count, 0, 0, 0, 0, 0, 0, 0, 0);
    if (capability == NULL) {
        mark_failed_with_message(env, "could not format terminal capability string", result);
        return NULL;
    }
    return byte_array_for_capability(env, capability, result);
}

JNIEXPORT jstring JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_getVersion(JNIEnv *env, jclass target) {
    return env->NewStringUTF(NATIVE_VERSION);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_initTerminal(JNIEnv *env, jclass target, jint output, jobject capabilities, jobject result) {
    if (!isatty(output+1)) {
        mark_failed_with_message(env, "not a terminal", result);
        return;
    }
    if (is_init == 0) {
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
    }
    is_init = 1;
}

JNIEXPORT jbyteArray JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_boldOn(JNIEnv *env, jclass target, jobject result) {
    return read_capability(env, getcap("md"), result);
}

JNIEXPORT jbyteArray JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_dimOn(JNIEnv *env, jclass target, jobject result) {
    return read_capability(env, getcap("mh"), result);
}

JNIEXPORT jbyteArray JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_reset(JNIEnv *env, jclass target, jobject result) {
    return read_capability(env, getcap("me"), result);
}

JNIEXPORT jbyteArray JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_foreground(JNIEnv *env, jclass target, jint color, jobject result) {
    return read_param_capability(env, getcap("AF"), color, result);
}

JNIEXPORT jbyteArray JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_up(JNIEnv *env, jclass target, jobject result) {
    return read_capability(env, getcap("up"), result);
}

JNIEXPORT jbyteArray JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_down(JNIEnv *env, jclass target, jobject result) {
    return read_capability(env, getcap("do"), result);
}

JNIEXPORT jbyteArray JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_left(JNIEnv *env, jclass target, jobject result) {
    return read_capability(env, getcap("le"), result);
}

JNIEXPORT jbyteArray JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_right(JNIEnv *env, jclass target, jobject result) {
    return read_capability(env, getcap("nd"), result);
}

JNIEXPORT jbyteArray JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_startLine(JNIEnv *env, jclass target, jobject result) {
    return read_capability(env, getcap("cr"), result);
}

JNIEXPORT jbyteArray JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_clearToEndOfLine(JNIEnv *env, jclass target, jobject result) {
    return read_capability(env, getcap("ce"), result);
}

JNIEXPORT jbyteArray JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_defaultForeground(JNIEnv *env, jclass target, jobject result) {
    return read_capability(env, getcap("op"), result);
}

JNIEXPORT jbyteArray JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_hideCursor(JNIEnv *env, jclass target, jobject result) {
    return read_capability(env, getcap("vi"), result);
}

JNIEXPORT jbyteArray JNICALL
Java_net_rubygrapefruit_platform_internal_jni_TerminfoFunctions_showCursor(JNIEnv *env, jclass target, jobject result) {
    return read_capability(env, getcap("ve"), result);
}

#endif
