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
 * POSIX platform functions.
 */
#ifndef WIN32

#include "native.h"
#include "generic.h"
#include <stdlib.h>
#include <errno.h>
#include <locale.h>
#include <xlocale.h>
#include <langinfo.h>
#include <string.h>

void mark_failed_with_errno(JNIEnv *env, const char* message, jobject result) {
    const char * errno_message = NULL;
    switch(errno) {
        case ENOENT:
            errno_message = "ENOENT";
            break;
    }

    mark_failed_with_code(env, message, errno, errno_message, result);
}

char* java_to_char(JNIEnv *env, jstring string, jobject result) {
    // TODO - share this code with nnn_getSystemInfo() below
    // Empty string means load locale from environment.
    locale_t locale = newlocale(LC_CTYPE_MASK, "", NULL);
    if (locale == NULL) {
        mark_failed_with_message(env, "could not create locale", result);
        return NULL;
    }

    jstring encoding = env->NewStringUTF(nl_langinfo_l(CODESET, locale));
    freelocale(locale);

    jclass strClass = env->FindClass("java/lang/String");
    jmethodID method = env->GetMethodID(strClass, "getBytes", "(Ljava/lang/String;)[B");
    jbyteArray byteArray = (jbyteArray)env->CallObjectMethod(string, method, encoding);
    size_t len = env->GetArrayLength(byteArray);
    char* chars = (char*)malloc(len + 1);
    env->GetByteArrayRegion(byteArray, 0, len, (jbyte*)chars);
    chars[len] = 0;

    return chars;
}

jstring char_to_java(JNIEnv* env, const char* chars, jobject result) {
    // TODO - share this code with nnn_getSystemInfo() below
    // Empty string means load locale from environment.
    locale_t locale = newlocale(LC_CTYPE_MASK, "", NULL);
    if (locale == NULL) {
        mark_failed_with_message(env, "could not create locale", result);
        return NULL;
    }
    jstring encoding = env->NewStringUTF(nl_langinfo_l(CODESET, locale));
    freelocale(locale);

    size_t len = strlen(chars);
    jbyteArray byteArray = env->NewByteArray(len);
    jbyte* bytes = env->GetByteArrayElements(byteArray, NULL);
    memcpy(bytes, chars, len);
    env->ReleaseByteArrayElements(byteArray, bytes, JNI_COMMIT);
    jclass strClass = env->FindClass("java/lang/String");
    jmethodID method = env->GetMethodID(strClass, "<init>", "([BLjava/lang/String;)V");
    return (jstring)env->NewObject(strClass, method, byteArray, encoding);
}

#endif
