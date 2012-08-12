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
        jstring mount_point = env->NewStringUTF(mount_info.mnt_dir);
        jstring file_system_type = env->NewStringUTF(mount_info.mnt_type);
        jstring device_name = env->NewStringUTF(mount_info.mnt_fsname);
        env->CallVoidMethod(info, method, mount_point, file_system_type, device_name, JNI_FALSE);
    }

    endmntent(fp);
}

#endif
