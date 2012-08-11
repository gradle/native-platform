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
        jstring mount_point = env->NewStringUTF(buf[i].f_mntonname);
        jstring file_system_type = env->NewStringUTF(buf[i].f_fstypename);
        jstring device_name = env->NewStringUTF(buf[i].f_mntfromname);
        jboolean remote = (buf[i].f_flags & MNT_LOCAL) == 0;
        env->CallVoidMethod(info, method, mount_point, file_system_type, device_name, remote);
    }
    free(buf);
}

#endif
