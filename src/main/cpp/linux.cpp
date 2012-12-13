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
    jmethodID method = env->GetMethodID(info_class, "add", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V");

    while (getmntent_r(fp, &mount_info, buf, sizeof(buf)) != NULL) {
        jstring mount_point = char_to_java(env, mount_info.mnt_dir, result);
        jstring file_system_type = char_to_java(env, mount_info.mnt_type, result);
        jstring device_name = char_to_java(env, mount_info.mnt_fsname, result);
        env->CallVoidMethod(info, method, mount_point, file_system_type, device_name, JNI_FALSE);
    }

    endmntent(fp);
}

#endif
