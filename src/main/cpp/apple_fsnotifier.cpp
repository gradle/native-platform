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
#include <pthread.h>
#include <strings.h>

CFMutableArrayRef rootsToWatch = NULL;
FSEventStreamRef watcherStream = NULL;
pthread_t watcherThread = NULL;
// store the callback object, as we need to invoke it once file change is detected.
jobject watcherCallback = NULL;
JavaVM* jvm = NULL;
CFRunLoopRef threadLoop = NULL;
bool invalidStateDetected = false;

typedef struct watch_details {
    char* message;
} watch_details_t;

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
        if (jvm->AttachCurrentThread((void **) &env, NULL) != JNI_OK) {
            invalidStateDetected = true;
            return;
        }
    } else if (getEnvStat == JNI_EVERSION) {
        invalidStateDetected = true;
        return;
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
    if (invalidStateDetected) return;
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

static void *EventProcessingThread(void *data) {
    FSEventStreamRef stream = (FSEventStreamRef) data;
    threadLoop = CFRunLoopGetCurrent();
    FSEventStreamScheduleWithRunLoop(stream, threadLoop, kCFRunLoopDefaultMode);
    FSEventStreamStart(stream);
    // This triggers run loop for this thread, causing it to run until we explicitly stop it.
    CFRunLoopRun();
    return NULL;
}

JNIEXPORT jobject JNICALL
Java_net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions_startWatch(JNIEnv *env, jclass target, jobjectArray paths, CFAbsoluteTime latency, jobject javaCallback, jobject result) {
    if (rootsToWatch == NULL) {
        invalidStateDetected = false;
        rootsToWatch = CFArrayCreateMutable(NULL, 0, NULL);
        if (rootsToWatch == NULL) {
            mark_failed_with_errno(env, "Could not allocate array to store roots to watch.", result);
            return NULL;
        }
    }
    int count = env->GetArrayLength(paths);
    if (count == 0) {
        mark_failed_with_errno(env, "No paths given to watch.", result);
        return NULL;
    }
    for (int i = 0; i < count; i++) {
        jstring path = (jstring) env->GetObjectArrayElement(paths, i);
        char* pathString = java_to_char(env, path, result);
        if (pathString == NULL) {
            mark_failed_with_errno(env, "Could not allocate string to store root to watch.", result);
            return NULL;
        }
        CFStringRef stringPath = CFStringCreateWithCString(NULL, pathString, kCFStringEncodingUTF8);
        free(pathString);
        if (stringPath == NULL) {
            mark_failed_with_errno(env, "Could not create CFStringRef.", result);
            return NULL;
        }
        CFArrayAppendValue(rootsToWatch, stringPath);
    }

    watcherCallback = env->NewGlobalRef(javaCallback);
    if (watcherCallback == NULL) {
        mark_failed_with_errno(env, "Could not create global reference for callback.", result);
        return NULL;
    }

    watcherStream = FSEventStreamCreate (
                NULL,
                &callback,
                NULL,
                rootsToWatch,
                kFSEventStreamEventIdSinceNow,
                latency,
                kFSEventStreamCreateFlagNoDefer);
    if (watcherStream == NULL) {
         mark_failed_with_errno(env, "Could not create FSEventStreamCreate to track changes.", result);
         return NULL;
    }

    if (pthread_create(&watcherThread, NULL, EventProcessingThread, watcherStream) != 0) {
        mark_failed_with_errno(env, "Could not create file watcher thread.", result);
        return NULL;
    }

    int jvmStatus = env->GetJavaVM(&jvm);
    if (jvmStatus < 0) {
        mark_failed_with_errno(env, "Could not store jvm instance.", result);
        return NULL;
    }

    jclass clsWatch = env->FindClass("net/rubygrapefruit/platform/internal/jni/DefaultOsxFileEventFunctions$WatchImpl");
    jmethodID constructor = env->GetMethodID(clsWatch, "<init>", "(Ljava/lang/Object;)V");
    watch_details_t* details = (watch_details_t*)malloc(sizeof(watch_details_t));
    details->message = (char*) "alma";
    return env->NewObject(clsWatch, constructor, env->NewDirectByteBuffer(details, sizeof(details)));
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions_stopWatch(JNIEnv *env, jclass target, jobject detailsObj, jobject result) {
    watch_details_t *details = (watch_details_t*) env->GetDirectBufferAddress(detailsObj);
    printf("~~~~ Hello %s\n", details->message);
    free(details);

    // if there were no roots to watch, there are no resources to release
    if (rootsToWatch == NULL) return;
    if (invalidStateDetected) {
        // report and reset flag, but try to clean up state as much as possible
        mark_failed_with_errno(env, "Watcher is in invalid state, reported changes may be incorrect.", result);
    }

    for (int i = 0; i < CFArrayGetCount(rootsToWatch); i++) {
        const void *value = CFArrayGetValueAtIndex(rootsToWatch, i);
        CFRelease(value);
    }
    CFRelease(rootsToWatch);
    rootsToWatch = NULL;

    FSEventStreamStop(watcherStream);
    FSEventStreamInvalidate(watcherStream);
    FSEventStreamRelease(watcherStream);
    // TODO: consider using FSEventStreamFlushSync to flush all pending events.
    watcherStream = NULL;

    CFRunLoopStop(threadLoop);
    threadLoop = NULL;

    env->DeleteGlobalRef(watcherCallback);
    watcherCallback = NULL;

    pthread_join(watcherThread, NULL);
    watcherThread = NULL;
}

#endif