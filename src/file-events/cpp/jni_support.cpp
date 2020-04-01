#include <iostream>

#include "jni_support.h"

using namespace std;

JavaVM* getJavaVm(JNIEnv* env) {
    JavaVM* jvm;
    int jvmStatus = env->GetJavaVM(&jvm);
    if (jvmStatus != 0) {
        throw runtime_error(string("Could not get jvm instance: ") + to_string(jvmStatus));
    }
    return jvm;
}

JniSupport::JniSupport(JavaVM* jvm)
    : jvm(jvm) {
}

JniSupport::JniSupport(JNIEnv* env)
    : jvm(getJavaVm(env)) {
}

JNIEnv* JniSupport::getThreadEnv() {
    JNIEnv* env;
    jint ret = jvm->GetEnv((void**) &env, JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        throw runtime_error(string("Failed to get JNI env for current thread: ") + to_string(ret));
    }
    return env;
}

void JniSupport::rethrowJavaException(JNIEnv* env) {
    jthrowable exception = env->ExceptionOccurred();
    if (exception != nullptr) {
        env->ExceptionDescribe();
        env->ExceptionClear();

        jclass exceptionClass = env->GetObjectClass(exception);
        jmethodID getMessage = env->GetMethodID(exceptionClass, "getMessage", "()Ljava/lang/String;");
        jstring javaMessage = (jstring) env->CallObjectMethod(exception, getMessage);
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            throw runtime_error("Couldn't get exception message");
        }
        string message = javaToUtf8String(env, javaMessage);
        env->DeleteLocalRef(javaMessage);

        jmethodID getClassName = env->GetMethodID(jniConstants->classClass.get(), "getName", "()Ljava/lang/String;");
        jstring javaExceptionType = (jstring) env->CallObjectMethod(exceptionClass, getClassName);
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            throw runtime_error("Couldn't get exception type");
        }
        string exceptionType = javaToUtf8String(env, javaExceptionType);
        env->DeleteLocalRef(javaExceptionType);

        env->DeleteLocalRef(exceptionClass);
        env->DeleteLocalRef(exception);

        throw runtime_error("Caught " + exceptionType + " with message: " + message);
    }
}

JniThreadAttacher::JniThreadAttacher(JavaVM* jvm, const char* name, bool daemon)
    : JniSupport(jvm) {
    JNIEnv* env;
    JavaVMAttachArgs args = {
        JNI_VERSION_1_6,            // version
        const_cast<char*>(name),    // thread name
        NULL                        // thread group
    };
    jint ret = daemon
        ? jvm->AttachCurrentThreadAsDaemon((void**) &env, (void*) &args)
        : jvm->AttachCurrentThread((void**) &env, (void*) &args);
    if (ret != JNI_OK) {
        cerr << "Failed to attach JNI to current thread: " << ret << endl;
        throw runtime_error(string("Failed to attach JNI to current thread: ") + to_string(ret));
    }
}

JniThreadAttacher::~JniThreadAttacher() {
    jint ret = jvm->DetachCurrentThread();
    if (ret != JNI_OK) {
        cerr << "Failed to detach JNI from current thread: " << ret << endl;
    }
}

JniConstants::JniConstants(JavaVM* jvm)
    : JniSupport(jvm)
    , classClass(getThreadEnv(), "java/lang/Class") {
}

string javaToUtf8String(JNIEnv* env, jstring javaString) {
    return utf16ToUtf8String(javaToUtf16String(env, javaString));
}

u16string javaToUtf16String(JNIEnv* env, jstring javaString) {
    jsize length = env->GetStringLength(javaString);
    const jchar* javaChars = env->GetStringCritical(javaString, nullptr);
    if (javaChars == NULL) {
        throw runtime_error("Could not get Java string character");
    }
    u16string path((char16_t*) javaChars, length);
    env->ReleaseStringCritical(javaString, javaChars);
    return path;
}

void javaToUtf16StringArray(JNIEnv* env, jobjectArray javaStrings, vector<u16string>& strings) {
    int count = env->GetArrayLength(javaStrings);
    strings.reserve(count);
    for (int i = 0; i < count; i++) {
        jstring javaString = reinterpret_cast<jstring>(env->GetObjectArrayElement(javaStrings, i));
        auto string = javaToUtf16String(env, javaString);
        strings.push_back(move(string));
    }
}

// Utility wrapper to adapt locale-bound facets for wstring convert
// Exposes the protected destructor as public
// See https://en.cppreference.com/w/cpp/locale/codecvt
template <class Facet>
struct deletable_facet : Facet {
    template <class... Args>
    deletable_facet(Args&&... args)
        : Facet(forward<Args>(args)...) {
    }
    ~deletable_facet() {
    }
};

u16string utf8ToUtf16String(const char* string) {
    wstring_convert<deletable_facet<codecvt<char16_t, char, mbstate_t>>, char16_t> conv16;
    return conv16.from_bytes(string);
}

string utf16ToUtf8String(const u16string& string) {
    wstring_convert<deletable_facet<codecvt<char16_t, char, mbstate_t>>, char16_t> conv16;
    return conv16.to_bytes(string);
}
