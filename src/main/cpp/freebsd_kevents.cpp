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
 * kevents backed file change event functions.
 */
#if defined(__APPLE__) || defined(__FreeBSD__)

#include "native.h"
#include "generic.h"
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/event.h>
#include <sys/time.h>

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
#if defined(O_EVTONLY)
    int event_fd = open(pathStr, O_EVTONLY);
#else
    int event_fd = open(pathStr, O_RDONLY);
#endif
    free(pathStr);
    if (event_fd == -1) {
        close(watch_fd);
        mark_failed_with_errno(env, "could not open path to watch for events", result);
        return NULL;
    }

    struct kevent event_spec;
    EV_SET( &event_spec, event_fd, EVFILT_VNODE, EV_ADD | EV_CLEAR, NOTE_DELETE |  NOTE_WRITE | NOTE_EXTEND | NOTE_ATTRIB | NOTE_LINK | NOTE_RENAME | NOTE_REVOKE, 0, NULL);
    int event_count = kevent(watch_fd, &event_spec, 1, NULL, 0, NULL);
    if (event_count < 0) {
        mark_failed_with_errno(env, "could not watch for changes", result);
        close(event_fd);
        close(watch_fd);
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
    struct kevent event;
    int event_count = kevent(details->watch_fd, NULL, 0, &event, 1, NULL);
    if (event_count < 0 && errno == EINTR) {
        return JNI_FALSE;
    }
    if ((event_count < 0) || (event.flags == EV_ERROR)) {
        mark_failed_with_errno(env, "could not receive next change event", result);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_FileEventFunctions_closeWatch(JNIEnv *env, jclass target, jobject handle, jobject result) {
    watch_details_t* details = (watch_details_t*)env->GetDirectBufferAddress(handle);
    close(details->target_fd);
    close(details->watch_fd);
    free(details);
}

#endif
