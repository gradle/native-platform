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
#ifndef _WIN32

#include "native.h"
#include "generic.h"
#include <stdlib.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/utsname.h>
#include <dirent.h>
#include <string.h>

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_NativeLibraryFunctions_getSystemInfo(JNIEnv *env, jclass target, jobject info, jobject result) {
    jclass infoClass = env->GetObjectClass(info);

    struct utsname machine_info;
    if (uname(&machine_info) != 0) {
        mark_failed_with_errno(env, "could not query machine details", result);
        return;
    }

    jfieldID osNameField = env->GetFieldID(infoClass, "osName", "Ljava/lang/String;");
    env->SetObjectField(info, osNameField, char_to_java(env, machine_info.sysname, result));
    jfieldID osVersionField = env->GetFieldID(infoClass, "osVersion", "Ljava/lang/String;");
    env->SetObjectField(info, osVersionField, char_to_java(env, machine_info.release, result));
    jfieldID machineArchitectureField = env->GetFieldID(infoClass, "machineArchitecture", "Ljava/lang/String;");
    env->SetObjectField(info, machineArchitectureField, char_to_java(env, machine_info.machine, result));
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixTypeFunctions_getNativeTypeInfo(JNIEnv *env, jclass target, jobject info) {
    jclass infoClass = env->GetObjectClass(info);
    env->SetIntField(info, env->GetFieldID(infoClass, "int_bytes", "I"), sizeof(int));
    env->SetIntField(info, env->GetFieldID(infoClass, "u_long_bytes", "I"), sizeof(u_long));
    env->SetIntField(info, env->GetFieldID(infoClass, "size_t_bytes", "I"), sizeof(size_t));
    env->SetIntField(info, env->GetFieldID(infoClass, "uid_t_bytes", "I"), sizeof(uid_t));
    env->SetIntField(info, env->GetFieldID(infoClass, "gid_t_bytes", "I"), sizeof(gid_t));
    env->SetIntField(info, env->GetFieldID(infoClass, "off_t_bytes", "I"), sizeof(off_t));
}

/*
 * File functions
 */

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_chmod(JNIEnv *env, jclass target, jstring path, jint mode, jobject result) {
    char* pathStr = java_to_char(env, path, result);
    if (pathStr == NULL) {
        return;
    }
    int retval = chmod(pathStr, mode);
    free(pathStr);
    if (retval != 0) {
        mark_failed_with_errno(env, "could not chmod file", result);
    }
}

jlong toMillis(struct timespec t) {
    return (jlong)(t.tv_sec) * 1000 + (jlong)(t.tv_nsec) / 1000000;
}

void unpackStat(struct stat* fileInfo, jint* type, jlong* size, jlong* lastModified) {
    switch (fileInfo->st_mode & S_IFMT) {
        case S_IFREG:
            *type = FILE_TYPE_FILE;
            *size = fileInfo->st_size;
            break;
        case S_IFDIR:
            *type = FILE_TYPE_DIRECTORY;
            *size = 0;
            break;
        case S_IFLNK:
            *type = FILE_TYPE_SYMLINK;
            *size = 0;
            break;
        default:
            *type = FILE_TYPE_OTHER;
            *size = 0;
    }
#ifdef __linux__
    *lastModified = toMillis(fileInfo->st_mtim);
#else
    *lastModified = toMillis(fileInfo->st_mtimespec);
#endif
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_stat(JNIEnv *env, jclass target, jstring path, jboolean followLink, jobject dest, jobject result) {
    jclass destClass = env->GetObjectClass(dest);
    jmethodID mid = env->GetMethodID(destClass, "details", "(IIIIJJI)V");
    if (mid == NULL) {
        mark_failed_with_message(env, "could not find method", result);
        return;
    }

    struct stat fileInfo;
    char* pathStr = java_to_char(env, path, result);
    if (pathStr == NULL) {
        return;
    }
    int retval;
    if (followLink) {
        retval = stat(pathStr, &fileInfo);
    } else {
        retval = lstat(pathStr, &fileInfo);
    }
    free(pathStr);
    if (retval != 0 && errno != ENOENT) {
        mark_failed_with_errno(env, "could not stat file", result);
        return;
    }

    if (retval != 0) {
        env->CallVoidMethod(dest, mid, FILE_TYPE_MISSING, (jint)0, (jint)0, (jint)0, (jlong)0, (jlong)0, (jint)0);
    } else {
        jint type;
        jlong size;
        jlong lastModified;
        unpackStat(&fileInfo, &type, &size, &lastModified);
        env->CallVoidMethod(dest,
                            mid,
                            type,
                            (jint)0777 & fileInfo.st_mode,
                            (jint)fileInfo.st_uid,
                            (jint)fileInfo.st_gid,
                            size,
                            lastModified,
                            (jint)fileInfo.st_blksize);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_readdir(JNIEnv *env, jclass target, jstring path, jboolean followLink, jobject contents, jobject result) {
    jclass contentsClass = env->GetObjectClass(contents);
    jmethodID mid = env->GetMethodID(contentsClass, "addFile", "(Ljava/lang/String;IJJ)V");
    if (mid == NULL) {
        mark_failed_with_message(env, "could not find method", result);
        return;
    }

    char* pathStr = java_to_char(env, path, result);
    if (pathStr == NULL) {
        return;
    }
    long pathLen = strlen(pathStr);
    DIR* dir = opendir(pathStr);
    if (dir == NULL) {
        mark_failed_with_errno(env, "could not open directory", result);
        free(pathStr);
        return;
    }
    struct dirent entry;
    struct dirent* next;
    while (true) {
        if (readdir_r(dir, &entry, &next) != 0) {
            mark_failed_with_errno(env, "could not read directory entry", result);
            break;
        }
        if (next == NULL) {
            break;
        }
        if (strcmp(".", entry.d_name) == 0 || strcmp("..", entry.d_name) == 0) {
            continue;
        }

        size_t childPathLen = pathLen + strlen(entry.d_name) + 2;
        char* childPath = (char*)malloc(childPathLen);
        strncpy(childPath, pathStr, pathLen);
        childPath[pathLen] = '/';
        strcpy(childPath+pathLen+1, entry.d_name);

        struct stat fileInfo;
        int retval;
        if (followLink) {
            retval = stat(childPath, &fileInfo);
        } else {
            retval = lstat(childPath, &fileInfo);
        }
        free(childPath);
        if (retval != 0) {
            mark_failed_with_errno(env, "could not stat file", result);
            break;
        }

        jint type;
        jlong size;
        jlong lastModified;
        unpackStat(&fileInfo, &type, &size, &lastModified);

        jstring childName = char_to_java(env, entry.d_name, result);
        env->CallVoidMethod(contents, mid, childName, type, size, lastModified);
    }

    closedir(dir);
    free(pathStr);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_symlink(JNIEnv *env, jclass target, jstring path, jstring contents, jobject result) {
    char* pathStr = java_to_char(env, path, result);
    if (pathStr == NULL) {
        return;
    }
    char* contentStr = java_to_char(env, contents, result);
    if (contentStr == NULL) {
        free(pathStr);
        return;
    }
    int retval = symlink(contentStr, pathStr);
    free(contentStr);
    free(pathStr);
    if (retval != 0) {
        mark_failed_with_errno(env, "could not symlink", result);
    }
}

JNIEXPORT jstring JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_readlink(JNIEnv *env, jclass target, jstring path, jobject result) {
    struct stat link_info;
    char* pathStr = java_to_char(env, path, result);
    if (pathStr == NULL) {
        return NULL;
    }
    int retval = lstat(pathStr, &link_info);
    if (retval != 0) {
        free(pathStr);
        mark_failed_with_errno(env, "could not lstat file", result);
        return NULL;
    }

    char* contents = (char*)malloc(link_info.st_size + 1);
    if (contents == NULL) {
        free(pathStr);
        mark_failed_with_message(env, "could not create array", result);
        return NULL;
    }

    retval = readlink(pathStr, contents, link_info.st_size);
    free(pathStr);
    if (retval < 0) {
        free(contents);
        mark_failed_with_errno(env, "could not readlink", result);
        return NULL;
    }
    contents[link_info.st_size] = 0;
    jstring contents_str = char_to_java(env, contents, result);
    free(contents);
    return contents_str;
}

/*
 * Process functions
 */

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getPid(JNIEnv *env, jclass target) {
    return getpid();
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_detach(JNIEnv *env, jclass target, jobject result) {
    if (setsid() == -1) {
        // Ignore if the error is that the process is already detached from the terminal
        if (errno != EPERM) {
            mark_failed_with_errno(env, "could not setsid()", result);
        }
    }
}

JNIEXPORT jstring JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getWorkingDirectory(JNIEnv *env, jclass target, jobject result) {
    char* path = getcwd(NULL, 0);
    if (path == NULL) {
        mark_failed_with_errno(env, "could not getcwd()", result);
        return NULL;
    }
    jstring dir = char_to_java(env, path, result);
    free(path);
    return dir;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_setWorkingDirectory(JNIEnv *env, jclass target, jstring dir, jobject result) {
    char* path = java_to_char(env, dir, result);
    if (path == NULL) {
        return;
    }
    if (chdir(path) != 0) {
        mark_failed_with_errno(env, "could not setcwd()", result);
    }
    free(path);
}

JNIEXPORT jstring JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getEnvironmentVariable(JNIEnv *env, jclass target, jstring var, jobject result) {
    char* varStr = java_to_char(env, var, result);
    char* valueStr = getenv(varStr);
    free(varStr);
    if (valueStr == NULL) {
        return NULL;
    }
    return char_to_java(env, valueStr, result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_setEnvironmentVariable(JNIEnv *env, jclass target, jstring var, jstring value, jobject result) {
    char* varStr = java_to_char(env, var, result);
    if (value == NULL) {
        if (setenv(varStr, "", 1) != 0) {
            mark_failed_with_errno(env, "could not putenv()", result);
        }
    } else {
        char* valueStr = java_to_char(env, value, result);
        if (setenv(varStr, valueStr, 1) != 0) {
            mark_failed_with_errno(env, "could not putenv()", result);
        }
        free(valueStr);
    }
    free(varStr);
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

#endif
