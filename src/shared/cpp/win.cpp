#ifdef _WIN32

#include "native.h"
#include "generic.h"
#include "win.h"
#include <stdlib.h>
#include <wchar.h>

jstring wchar_to_java(JNIEnv* env, const wchar_t* chars, size_t len, jobject result) {
    if (sizeof(wchar_t) != 2) {
        // TODO We should check this somewhere else and ditch requiring a result parameter
        mark_failed_with_message(env, "unexpected size of wchar_t", result);
        return NULL;
    }
    return env->NewString((jchar*)chars, len);
}

wchar_t* java_to_wchar(JNIEnv *env, jstring string, jobject result) {
    jsize len = env->GetStringLength(string);
    wchar_t* str = (wchar_t*)malloc(sizeof(wchar_t) * (len+1));
    env->GetStringRegion(string, 0, len, (jchar*)str);
    str[len] = L'\0';
    return str;
}

wchar_t* java_to_wchar_path(JNIEnv *env, jstring string, jobject result) {
    // Copy the Java string into a UTF-16 string.
    jsize len = env->GetStringLength(string);
    wchar_t* str = (wchar_t*)malloc(sizeof(wchar_t) * (len+1));
    env->GetStringRegion(string, 0, len, (jchar*)str);
    str[len] = L'\0';

    // Technically, this should be MAX_PATH (i.e. 260), except some Win32 API related
    // to working with directory paths are actually limited to 240. It is just
    // safer/simpler to cover both cases in one code path.
    if (len <= 240) {
        return str;
    }

    if (is_path_absolute_local(str, len)) {
        // Format: C:\... -> \\?\C:\...
        wchar_t* str2 = add_prefix(str, len, L"\\\\?\\");
        free(str);
        return str2;
    } else if (is_path_absolute_unc(str, len)) {
        // In this case, we need to skip the first 2 characters:
        // Format: \\server\share\... -> \\?\UNC\server\share\...
        wchar_t* str2 = add_prefix(&str[2], len - 2, L"\\\\?\\UNC\\");
        free(str);
        return str2;
    }
    else {
        // It is some sort of unknown format, don't mess with it
        return str;
    }
}

bool is_path_absolute_local(wchar_t* path, int path_len) {
    if (path_len < 3) {
        return false;
    }
    return (('a' <= path[0] && path[0] <= 'z') || ('A' <= path[0] && path[0] <= 'Z')) &&
        path[1] == ':' &&
        path[2] == '\\';
}

bool is_path_absolute_unc(wchar_t* path, int path_len) {
    if (path_len < 3) {
        return false;
    }
    return path[0] == '\\' && path[1] == '\\';
}

wchar_t* add_prefix(wchar_t* path, int path_len, wchar_t* prefix) {
    int prefix_len = wcslen(prefix);
    int str_len = path_len + prefix_len;
    wchar_t* str = (wchar_t*)malloc(sizeof(wchar_t) * (str_len + 1));
    wcscpy_s(str, str_len + 1, prefix);
    wcsncat_s(str, str_len + 1, path, path_len);
    return str;
}

wchar_t* add_suffix(wchar_t* path, int path_len, wchar_t* suffix) {
    int suffix_len = wcslen(suffix);
    int str_len = path_len + suffix_len;
    wchar_t* str = (wchar_t*)malloc(sizeof(wchar_t) * (str_len + 1));
    wcsncpy_s(str, str_len + 1, path, path_len);
    wcscat_s(str, str_len + 1, suffix);
    return str;
}

int minimumLogLevel;
jclass clsLogger;
jmethodID logMethod;

JNIEXPORT void JNICALL Java_net_rubygrapefruit_platform_internal_jni_NativeLogger_initLogging(JNIEnv *env, jclass target, jint level) {
    minimumLogLevel = (int) level;
    clsLogger = env->FindClass("net/rubygrapefruit/platform/internal/jni/NativeLogger");
    logMethod = env->GetStaticMethodID(clsLogger, "log", "(ILjava/lang/String;)V");
    printlog(env, LOG_CONFIG, L"Initialized logging to level %d\n", level);
}

void printlog(JNIEnv* env, int level, const wchar_t* fmt, ...) {
    if (minimumLogLevel > level) {
        return;
    }

    wchar_t buffer[1024];
    va_list args;
    va_start(args, fmt);
    _vsnwprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);

    env->CallStaticVoidMethod(clsLogger, logMethod, level, wchar_to_java(env, buffer, wcslen(buffer), NULL));
}

#endif
