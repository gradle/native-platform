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
 * Apple specific functions.
 */
#if defined(__APPLE__)

#include "native.h"
#include "generic.h"
#include <string.h>
#include <stdlib.h>
#include <sys/param.h>
#include <sys/ucred.h>
#include <sys/mount.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/attr.h>
#include <sys/event.h>
#include <sys/time.h>

typedef struct vol_caps_buf {
    u_int32_t size;
    vol_capabilities_attr_t caps;
} vol_caps_buf_t;

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
    jmethodID method = env->GetMethodID(info_class, "add", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZZ)V");

    for (int i = 0; i < fs_count; i++) {
        jboolean caseSensitive = JNI_TRUE;
        jboolean casePreserving = JNI_TRUE;

        struct attrlist alist;
        memset(&alist, 0, sizeof(alist));
        alist.bitmapcount = ATTR_BIT_MAP_COUNT;
        alist.volattr = ATTR_VOL_CAPABILITIES | ATTR_VOL_INFO;
        vol_caps_buf_t buffer;

        // getattrlist requires the path to the actual mount point.
        int err = getattrlist(buf[i].f_mntonname, &alist, &buffer, sizeof(buffer), 0);
        if (err != 0) {
            mark_failed_with_errno(env, "could not determine file system attributes", result);
            break;
        }

        if (alist.volattr & ATTR_VOL_CAPABILITIES) {
            if ((buffer.caps.valid[VOL_CAPABILITIES_FORMAT] & VOL_CAP_FMT_CASE_SENSITIVE)) {
                caseSensitive = (buffer.caps.capabilities[VOL_CAPABILITIES_FORMAT] & VOL_CAP_FMT_CASE_SENSITIVE) != 0;
            }
            if ((buffer.caps.valid[VOL_CAPABILITIES_FORMAT] & VOL_CAP_FMT_CASE_PRESERVING)) {
                casePreserving = (buffer.caps.capabilities[VOL_CAPABILITIES_FORMAT] & VOL_CAP_FMT_CASE_PRESERVING) != 0;
            }
        }

        jstring mount_point = char_to_java(env, buf[i].f_mntonname, result);
        jstring file_system_type = char_to_java(env, buf[i].f_fstypename, result);
        jstring device_name = char_to_java(env, buf[i].f_mntfromname, result);
        jboolean remote = (buf[i].f_flags & MNT_LOCAL) == 0;
        env->CallVoidMethod(info, method, mount_point, file_system_type, device_name, remote, caseSensitive, casePreserving);
    }
    free(buf);
}

typedef struct watch_details {
    int watch_fd;
    int target_fd;
} watch_details_t;

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_FileEventFunctions_createWatch(JNIEnv *env, jclass target, jstring path, jobject result) {
    int watch_fd = kqueue();
    if (watch_fd == -1) {
        mark_failed_with_errno(env, "could not create kqueue", result);
        return NULL;
    }
    char* pathStr = java_to_char(env, path, result);
    int event_fd = open(pathStr, O_EVTONLY);
    free(pathStr);
    if (event_fd == -1) {
        close(watch_fd);
        mark_failed_with_errno(env, "could not open path to watch for events", result);
        return NULL;
    }
    watch_details_t* details = (watch_details_t*)malloc(sizeof(watch_details_t));
    details->watch_fd = watch_fd;
    details->target_fd = event_fd;
    return env->NewDirectByteBuffer(details, sizeof(watch_details_t));
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_FileEventFunctions_waitForNextEvent(JNIEnv *env, jclass target, jobject handle, jobject result) {
    watch_details_t* details = (watch_details_t*)env->GetDirectBufferAddress(handle);
    struct kevent event_spec;
    EV_SET( &event_spec, details->target_fd, EVFILT_VNODE, EV_ADD | EV_CLEAR, NOTE_DELETE |  NOTE_WRITE | NOTE_EXTEND | NOTE_ATTRIB | NOTE_LINK | NOTE_RENAME | NOTE_REVOKE, 0, NULL);
    struct kevent event;
    int event_count = kevent(details->watch_fd, &event_spec, 1, &event, 1, NULL);
    if ((event_count < 0) || (event.flags == EV_ERROR)) {
        mark_failed_with_errno(env, "could not receive next change event", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_FileEventFunctions_closeWatch(JNIEnv *env, jclass target, jobject handle, jobject result) {
    watch_details_t* details = (watch_details_t*)env->GetDirectBufferAddress(handle);
    close(details->target_fd);
    close(details->watch_fd);
    free(details);
}

#endif
