#pragma once

#include <jni.h>
#include <stdexcept>

using namespace std;

template <typename T>
class JniGlobalRef;

/**
 * Support for using JNI in a multi-threaded environment.
 */
class JniSupport {
public:
    JniSupport(JavaVM* jvm);
    JniSupport(JNIEnv* env);

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

/**
 * Attach a native thread to JNI.
 */
class JniThreadAttacher : public JniSupport {
public:
    JniThreadAttacher(JavaVM* jvm, const char* name, bool daemon);
    ~JniThreadAttacher();
};
