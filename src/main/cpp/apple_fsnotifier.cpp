/*
 Initial version copied from https://raw.githubusercontent.com/JetBrains/intellij-community/master/native/fsNotifier/mac/.
 */
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Apple specific functions.
 */
#if defined(__APPLE__)

#include "native.h"
#include "generic.h"
#include <CoreServices/CoreServices.h>
#include <thread>
#include <stdio.h>
#include <pthread.h>
#include <strings.h>
#include <sys/mount.h>

CFMutableArrayRef rootsToWatch;
FSEventStreamRef watcherStream;
pthread_t watcherThread;
// store the callback object, as we need to invoke it once file change is detected.
jobject watcherCallback;
JavaVM* jvm;
CFRunLoopRef threadLoop;


static void reportEvent(const char *event, char *path) {
    size_t len = 0;
    if (path != NULL) {
        len = strlen(path);
        for (char *p = path; *p != '\0'; p++) {
            if (*p == '\n') {
                *p = '\0';
            }
        }
    }
    JNIEnv* env;
    int getEnvStat = jvm->GetEnv((void **)&env, JNI_VERSION_1_6);
    if (getEnvStat == JNI_EDETACHED) {
        if (jvm->AttachCurrentThread((void **) &env, NULL) != 0) {
        }
    } else if (getEnvStat == JNI_OK) {
        //
    } else if (getEnvStat == JNI_EVERSION) {
        printf("GetEnv: version not supported");
    }

    jclass callback_class = env->GetObjectClass(watcherCallback);
    jmethodID methodCallback = env->GetMethodID(callback_class, "pathChanged", "(Ljava/lang/String;)V");
    env->CallVoidMethod(watcherCallback, methodCallback, env->NewStringUTF(path));
}

static void callback(ConstFSEventStreamRef streamRef,
                     void *clientCallBackInfo,
                     size_t numEvents,
                     void *eventPaths,
                     const FSEventStreamEventFlags eventFlags[],
                     const FSEventStreamEventId eventIds[]) {
    char **paths = (char**) eventPaths;

    for (int i = 0; i < numEvents; i++) {
        // TODO[max] Lion has much more detailed flags we need accurately process. For now just reduce to SL events range.
        FSEventStreamEventFlags flags = eventFlags[i] & 0xFF;
        if ((flags & kFSEventStreamEventFlagMustScanSubDirs) != 0) {
            reportEvent("RECDIRTY", paths[i]);
        } else if (flags != kFSEventStreamEventFlagNone) {
            reportEvent("RESET", NULL);
        } else {
            reportEvent("DIRTY", paths[i]);
        }
    }
}



JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions_createWatch(JNIEnv *env, jclass target, jstring path, jobject result) {
    if (rootsToWatch == NULL) {
        rootsToWatch = CFArrayCreateMutable(NULL, 0, NULL);
    }
    CFStringRef stringPath = CFStringCreateWithCString(NULL, java_to_char(env, path, result), kCFStringEncodingUTF8);
    CFArrayAppendValue(rootsToWatch, stringPath);
}

static void *EventProcessingThread(void *data) {
    FSEventStreamRef stream = (FSEventStreamRef) data;
    threadLoop = CFRunLoopGetCurrent();
    FSEventStreamScheduleWithRunLoop(stream, CFRunLoopGetCurrent(), kCFRunLoopDefaultMode);
    FSEventStreamStart(stream);
    CFRunLoopRun();
    return NULL;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions_startWatch(JNIEnv *env, jclass target, jobject javaCallback, jobject result) {
    if (rootsToWatch == NULL) {
        // nothing to watch, just return
        return;
    }
    CFAbsoluteTime latency = 0.3;  // Latency in seconds

    watcherCallback = env->NewGlobalRef(javaCallback);

    watcherStream = FSEventStreamCreate (
                NULL,
                &callback,
                NULL,
                rootsToWatch,
                kFSEventStreamEventIdSinceNow,
                latency,
                kFSEventStreamCreateFlagNoDefer);
    if (watcherStream == NULL) {
        printf("GIVEUP\n");
    }

    if (pthread_create(&watcherThread, NULL, EventProcessingThread, watcherStream) != 0) {
        printf("GIVEUP\n");
    }

    env->GetJavaVM(&jvm);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions_stopWatch(JNIEnv *env, jclass target, jobject result) {
    // if there were no roots to watch, there are no resources to release
    if (rootsToWatch == NULL) return;

    for (int i = 0; i < CFArrayGetCount(rootsToWatch); i++) {
        void *value = (char *)CFArrayGetValueAtIndex(rootsToWatch, i);
        free(value);
    }
    CFRelease(rootsToWatch);
    rootsToWatch = NULL;

    FSEventStreamStop(watcherStream);
    watcherStream = NULL;
    CFRunLoopStop(threadLoop);
    threadLoop = NULL;
    env->DeleteGlobalRef(watcherCallback);
    watcherCallback = NULL;
    pthread_join(watcherThread, NULL);
    watcherThread = NULL;
}

#endif