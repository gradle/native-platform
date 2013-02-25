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
#include <string.h>
#include <wchar.h>

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
    size_t stringLen = env->GetStringLength(string);
    wchar_t* wideString = (wchar_t*)malloc(sizeof(wchar_t) * (stringLen+1));
    const jchar* javaString = env->GetStringChars(string, NULL);
    for (size_t i = 0; i < stringLen; i++) {
        wideString[i] = javaString[i];
    }
    wideString[stringLen] = L'\0';
    env->ReleaseStringChars(string, javaString);

    size_t bytes = wcstombs(NULL, wideString, 0);
    if (bytes < 0) {
        mark_failed_with_message(env, "could not convert string to current locale", result);
        free(wideString);
        return NULL;
    }

    char* chars = (char*)malloc(bytes + 1);
    wcstombs(chars, wideString, bytes+1);
    free(wideString);

    return chars;
}

jstring char_to_java(JNIEnv* env, const char* chars, jobject result) {
    size_t bytes = strlen(chars);
    wchar_t* wideString = (wchar_t*)malloc(sizeof(wchar_t) * (bytes+1));
    if (mbstowcs(wideString, chars, bytes+1) < 0) {
        mark_failed_with_message(env, "could not convert string from current locale", result);
        free(wideString);
        return NULL;
    }
    size_t stringLen = wcslen(wideString);
    jchar* javaString = (jchar*)malloc(sizeof(jchar) * stringLen);
    for (int i =0; i < stringLen; i++) {
        javaString[i] = (jchar)wideString[i];
    }
    jstring string = env->NewString(javaString, stringLen);
    free(wideString);
    free(javaString);
    return string;
}

#endif
