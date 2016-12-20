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
#ifdef __APPLE__

#include "native.h"
#include "generic.h"
#include <stdlib.h>
#include <string.h>
#include <wchar.h>

char* java_to_char(JNIEnv *env, jstring string, jobject result) {
    size_t len = env->GetStringLength(string);
    size_t bytes = env->GetStringUTFLength(string);
    char* chars = (char*)malloc(bytes + 1);
    env->GetStringUTFRegion(string, 0, len, chars);
    chars[bytes] = 0;
    return chars;
}

jstring char_to_java(JNIEnv* env, const char* chars, jobject result) {
    return env->NewStringUTF(chars);
}

#include <sys/types.h>
#include <sys/sysctl.h>
#include <mach/mach.h>

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_MemoryFunctions_getMemoryInfo(JNIEnv *env, jclass type, jobject dest, jobject result) {
    jclass destClass = env->GetObjectClass(dest);
    jmethodID mid = env->GetMethodID(destClass, "details", "(JJ)V");
    if (mid == NULL) {
        mark_failed_with_message(env, "could not find method", result);
        return;
    }

    // Get total physical memory
    int mib[2];
    mib[0] = CTL_HW;
    mib[1] = HW_MEMSIZE;
    int64_t total_memory = 0;
    size_t len = sizeof(total_memory);
    if (sysctl(mib, 2, &total_memory, &len, NULL, 0) != 0) {
        mark_failed_with_errno(env, "could not query memory size", result);
        return;
    }

    // Calculate available system memory
    // This is an approximation due to the Darwin VM model
    // free + inactive - speculative pages
    vm_size_t page_size;
    mach_port_t mach_port;
    mach_msg_type_number_t count;
    vm_statistics64_data_t vm_stats;

    mach_port = mach_host_self();
    count = sizeof(vm_stats) / sizeof(natural_t);
    if (KERN_SUCCESS != host_page_size(mach_port, &page_size)) {
        mark_failed_with_errno(env, "could not query page size", result);
        return;
    }
    if (KERN_SUCCESS != host_statistics64(mach_port, HOST_VM_INFO, (host_info64_t)&vm_stats, &count)) {
        mark_failed_with_errno(env, "could not query host statistics", result);
        return;
    }
    long long available_memory = ((int64_t)vm_stats.free_count
                                 + (int64_t)vm_stats.inactive_count
                                 - (int64_t)vm_stats.speculative_count)
                                 * (int64_t)page_size;

    env->CallVoidMethod(dest, mid, (jlong)total_memory, (jlong)available_memory);
}

#endif
