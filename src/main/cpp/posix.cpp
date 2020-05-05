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

#include "generic.h"
#include "net_rubygrapefruit_platform_internal_jni_NativeLibraryFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_PosixFileFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_PosixTerminalFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_PosixTypeFunctions.h"
#include <dirent.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/utsname.h>
#include <termios.h>
#include <unistd.h>

jmethodID fileStatDetailsMethodId;

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_NativeLibraryFunctions_getSystemInfo(JNIEnv* env, jclass target, jobject info, jobject result) {
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
    jfieldID hostnameField = env->GetFieldID(infoClass, "hostname", "Ljava/lang/String;");
    env->SetObjectField(info, hostnameField, char_to_java(env, machine_info.nodename, result));
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixTypeFunctions_getNativeTypeInfo(JNIEnv* env, jclass target, jobject info) {
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
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_chmod(JNIEnv* env, jclass target, jstring path, jint mode, jobject result) {
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

void unpackStat(struct stat* source, file_stat_t* result) {
    switch (source->st_mode & S_IFMT) {
        case S_IFREG:
            result->fileType = FILE_TYPE_FILE;
            result->size = source->st_size;
            break;
        case S_IFDIR:
            result->fileType = FILE_TYPE_DIRECTORY;
            result->size = 0;
            break;
        case S_IFLNK:
            result->fileType = FILE_TYPE_SYMLINK;
            result->size = 0;
            break;
        default:
            result->fileType = FILE_TYPE_OTHER;
            result->size = 0;
    }
#ifdef __linux__
    result->lastModified = toMillis(source->st_mtim);
#else
    result->lastModified = toMillis(source->st_mtimespec);
#endif
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_stat(JNIEnv* env, jclass target, jstring path, jboolean followLink, jobject dest, jobject result) {
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
    if (retval != 0 && errno != ENOENT && errno != ENOTDIR) {
        mark_failed_with_errno(env, "could not stat file", result);
        return;
    }

    if (retval != 0) {
        env->CallVoidMethod(dest, fileStatDetailsMethodId, FILE_TYPE_MISSING, (jint) 0, (jint) 0, (jint) 0, (jlong) 0, (jlong) 0, (jint) 0);
    } else {
        file_stat_t fileResult;
        unpackStat(&fileInfo, &fileResult);
        env->CallVoidMethod(dest,
            fileStatDetailsMethodId,
            fileResult.fileType,
            (jint) (0777 & fileInfo.st_mode),
            (jint) fileInfo.st_uid,
            (jint) fileInfo.st_gid,
            fileResult.size,
            fileResult.lastModified,
            (jint) fileInfo.st_blksize);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_readdir(JNIEnv* env, jclass target, jstring path, jboolean followLink, jobject contents, jobject result) {
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
        char* childPath = (char*) malloc(childPathLen);
        strncpy(childPath, pathStr, pathLen);
        childPath[pathLen] = '/';
        strcpy(childPath + pathLen + 1, entry.d_name);

        struct stat fileInfo;
        int retval;
        if (followLink) {
            retval = stat(childPath, &fileInfo);
        } else {
            retval = lstat(childPath, &fileInfo);
        }
        free(childPath);
        file_stat fileResult;
        if (retval != 0) {
            if (!followLink || errno != ENOENT) {
                mark_failed_with_errno(env, "could not stat file", result);
                break;
            }
            fileResult.fileType = FILE_TYPE_MISSING;
            fileResult.size = 0;
            fileResult.lastModified = 0;
        } else {
            unpackStat(&fileInfo, &fileResult);
        }

        jstring childName = char_to_java(env, entry.d_name, result);
        env->CallVoidMethod(contents, mid, childName, fileResult.fileType, fileResult.size, fileResult.lastModified);
    }

    closedir(dir);
    free(pathStr);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_symlink(JNIEnv* env, jclass target, jstring path, jstring contents, jobject result) {
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
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_readlink(JNIEnv* env, jclass target, jstring path, jobject result) {
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

    char* contents = (char*) malloc(link_info.st_size + 1);
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
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getPid(JNIEnv* env, jclass target) {
    return getpid();
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_detach(JNIEnv* env, jclass target, jobject result) {
    if (setsid() == -1) {
        // Ignore if the error is that the process is already detached from the terminal
        if (errno != EPERM) {
            mark_failed_with_errno(env, "could not setsid()", result);
        }
    }
}

JNIEXPORT jstring JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getWorkingDirectory(JNIEnv* env, jclass target, jobject result) {
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
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_setWorkingDirectory(JNIEnv* env, jclass target, jstring dir, jobject result) {
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
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getEnvironmentVariable(JNIEnv* env, jclass target, jstring var, jobject result) {
    char* varStr = java_to_utf_char(env, var, result);
    char* valueStr = getenv(varStr);
    free(varStr);
    if (valueStr == NULL) {
        return NULL;
    }
    return utf_char_to_java(env, valueStr, result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_setEnvironmentVariable(JNIEnv* env, jclass target, jstring var, jstring value, jobject result) {
    char* varStr = java_to_utf_char(env, var, result);
    if (varStr != NULL) {
        if (value == NULL) {
            if (setenv(varStr, "", 1) != 0) {
                mark_failed_with_errno(env, "could not putenv()", result);
            }
        } else {
            char* valueStr = java_to_utf_char(env, value, result);
            if (valueStr != NULL) {
                if (setenv(varStr, valueStr, 1) != 0) {
                    mark_failed_with_errno(env, "could not putenv()", result);
                }
            }
            free(valueStr);
        }
    }
    free(varStr);
}

/*
 * Terminal functions
 */

JNIEXPORT jboolean JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixTerminalFunctions_isatty(JNIEnv* env, jclass target, jint output) {
    struct stat fileInfo;
    int result;
    switch (output) {
        case STDIN_DESCRIPTOR:
            return isatty(STDIN_FILENO) ? JNI_TRUE : JNI_FALSE;
        case STDOUT_DESCRIPTOR:
            return isatty(STDOUT_FILENO) ? JNI_TRUE : JNI_FALSE;
        case STDERR_DESCRIPTOR:
            return isatty(STDERR_FILENO) ? JNI_TRUE : JNI_FALSE;
        default:
            return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixTerminalFunctions_getTerminalSize(JNIEnv* env, jclass target, jint output, jobject dimension, jobject result) {
    struct winsize screen_size;
    int retval = ioctl(output + 1, TIOCGWINSZ, &screen_size);
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

int input_init = 0;
struct termios original_input_mode;

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixTerminalFunctions_rawInputMode(JNIEnv* env, jclass target, jobject result) {
    if (input_init == 0) {
        tcgetattr(STDIN_FILENO, &original_input_mode);
        input_init = 1;
    }
    struct termios new_mode;
    new_mode = original_input_mode;
    new_mode.c_lflag &= ~(ICANON | ECHO);
    tcsetattr(STDIN_FILENO, TCSANOW, &new_mode);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixTerminalFunctions_resetInputMode(JNIEnv* env, jclass target, jobject result) {
    if (input_init == 0) {
        return;
    }
    tcsetattr(STDIN_FILENO, TCSANOW, &original_input_mode);
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* jvm, void*) {
    JNIEnv* env;
    jint ret = jvm->GetEnv((void**) &env, JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        return -1;
    }
    jclass destClass = env->FindClass("net/rubygrapefruit/platform/internal/FileStat");
    fileStatDetailsMethodId = env->GetMethodID(destClass, "details", "(IIIIJJI)V");
    return JNI_VERSION_1_6;
}

#endif
