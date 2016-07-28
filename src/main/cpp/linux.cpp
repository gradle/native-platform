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
 * Linux specific functions.
 */
#ifdef __linux__

#include "native.h"
#include "generic.h"
#include <stdio.h>
#include <mntent.h>
#include <unistd.h>
#include <stdlib.h>
#include <dirent.h>
#include <sys/inotify.h>

/*
 * File system functions
 */
JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileSystemFunctions_listFileSystems(JNIEnv *env, jclass target, jobject info, jobject result) {
    FILE *fp = setmntent(MOUNTED, "r");
    if (fp == NULL) {
        mark_failed_with_errno(env, "could not open mount file", result);
        return;
    }
    char buf[1024];
    struct mntent mount_info;

    jclass info_class = env->GetObjectClass(info);
    jmethodID method = env->GetMethodID(info_class, "add", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZZ)V");

    while (getmntent_r(fp, &mount_info, buf, sizeof(buf)) != NULL) {
        jstring mount_point = char_to_java(env, mount_info.mnt_dir, result);
        jstring file_system_type = char_to_java(env, mount_info.mnt_type, result);
        jstring device_name = char_to_java(env, mount_info.mnt_fsname, result);
        env->CallVoidMethod(info, method, mount_point, file_system_type, device_name, JNI_FALSE, JNI_TRUE, JNI_TRUE);
    }

    endmntent(fp);
}

typedef struct watch_details {
    int watch_fd;
    int target_fd;
} watch_details_t;

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_FileEventFunctions_createWatch(JNIEnv *env, jclass target, jstring path, jobject result) {
#ifdef IN_CLOEXEC
    int watch_fd = inotify_init1(IN_CLOEXEC);
#else
    // Not available on older versions, fall back to inotify_init()
    int watch_fd = inotify_init();
#endif
    if (watch_fd == -1) {
        mark_failed_with_errno(env, "could not initialize inotify", result);
        return NULL;
    }
    char* pathStr = java_to_char(env, path, result);
    int event_fd = inotify_add_watch(watch_fd, pathStr, IN_ATTRIB | IN_CREATE | IN_DELETE | IN_DELETE_SELF | IN_MODIFY | IN_MOVE_SELF | IN_MOVED_FROM | IN_MOVED_TO);
    free(pathStr);
    if (event_fd == -1) {
        close(watch_fd);
        mark_failed_with_errno(env, "could not add path to watch", result);
        return NULL;
    }
    watch_details_t* details = (watch_details_t*)malloc(sizeof(watch_details_t));
    details->watch_fd = watch_fd;
    details->target_fd = event_fd;
    return env->NewDirectByteBuffer(details, sizeof(watch_details_t));
}

JNIEXPORT jboolean JNICALL
Java_net_rubygrapefruit_platform_internal_jni_FileEventFunctions_waitForNextEvent(JNIEnv *env, jclass target, jobject handle, jobject result) {
    watch_details_t* details = (watch_details_t*)env->GetDirectBufferAddress(handle);
    size_t len = sizeof(struct inotify_event) + NAME_MAX + 1;
    void* buffer = malloc(len);
    size_t read_count = read(details->watch_fd, buffer, len);
    free(buffer);
    if (read_count == -1) {
        mark_failed_with_errno(env, "could not wait for next event", result);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_FileEventFunctions_closeWatch(JNIEnv *env, jclass target, jobject handle, jobject result) {
    watch_details_t* details = (watch_details_t*)env->GetDirectBufferAddress(handle);
    inotify_rm_watch(details->watch_fd, details->target_fd);
    close(details->watch_fd);
    free(details);
}

#endif
