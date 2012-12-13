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
 * OS X specific functions.
 */
#ifdef __APPLE__

#include "native.h"
#include "generic.h"
#include <stdlib.h>
#include <sys/param.h>
#include <sys/ucred.h>
#include <sys/mount.h>

/*
 * File system functions
 */
JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileSystemFunctions_listFileSystems(JNIEnv *env, jclass target, jobject info, jobject result) {
    int fs_count = getfsstat(NULL, 0, MNT_NOWAIT);
    if (fs_count < 0) {
        mark_failed_with_errno(env, "could not stat file systems", result);
        return;
    }

    size_t len = fs_count * sizeof(struct statfs);
    struct statfs* buf = (struct statfs*)malloc(len);
    if (getfsstat(buf, len, MNT_NOWAIT) < 0 ) {
        mark_failed_with_errno(env, "could not stat file systems", result);
        free(buf);
        return;
    }

    jclass info_class = env->GetObjectClass(info);
    jmethodID method = env->GetMethodID(info_class, "add", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V");

    for (int i = 0; i < fs_count; i++) {
        jstring mount_point = char_to_java(env, buf[i].f_mntonname, result);
        jstring file_system_type = char_to_java(env, buf[i].f_fstypename, result);
        jstring device_name = char_to_java(env, buf[i].f_mntfromname, result);
        jboolean remote = (buf[i].f_flags & MNT_LOCAL) == 0;
        env->CallVoidMethod(info, method, mount_point, file_system_type, device_name, remote);
    }
    free(buf);
}

#endif
