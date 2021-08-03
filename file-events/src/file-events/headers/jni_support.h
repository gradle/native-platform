#pragma once

#include <jni.h>
#include <stdexcept>
#include <string>
#include <vector>

using namespace std;

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

template <typename T>
class JniGlobalRef;

/**
 * Support for using JNI in a multi-threaded environment.
 */
class JniSupport {
public:
    JniSupport(JavaVM* jvm);
    JniSupport(JNIEnv* env);

    /**
     * Check for a Java exception and log it.
     */
    static jthrowable getJavaExceptionAndPrintStacktrace(JNIEnv* env);

    /**
     * Check for a Java exception and rethrow as a native exception.
     */
    static void rethrowJavaException(JNIEnv* env);

protected:
    const JniGlobalRef<jclass>& findClass(const char* className);
    JNIEnv* getThreadEnv();

protected:
    JavaVM* jvm;
};

template <typename T>
class JniGlobalRef : public JniSupport {
public:
    JniGlobalRef(JNIEnv* env, T object)
        : JniSupport(env)
        , ref(reinterpret_cast<T>(env->NewGlobalRef(object))) {
        if (ref == nullptr) {
            throw runtime_error("Failed to create global JNI reference");
        }
    }
    ~JniGlobalRef() {
        getThreadEnv()->DeleteGlobalRef(ref);
    }

    T get() const {
        return ref;
    }

private:
    const T ref;
};

class JClass : public JniGlobalRef<jclass> {
public:
    JClass(JNIEnv* env, const char* className)
        : JniGlobalRef(env, env->FindClass(className)) {
    }
};

class BaseJniConstants : public JniSupport {
public:
    BaseJniConstants(JavaVM* jvm);

    const JClass classClass;
};

extern BaseJniConstants* baseJniConstants;

extern string javaToUtf8String(JNIEnv* env, jstring javaString);

extern u16string javaToUtf16String(JNIEnv* env, jstring javaString);

extern void javaToUtf16StringArray(JNIEnv* env, jobjectArray javaStrings, vector<u16string>& strings);

extern u16string utf8ToUtf16String(const char* string);

extern string utf16ToUtf8String(const u16string& string);
