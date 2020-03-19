#pragma once

#include <jni.h>

/**
 * Support for using JNI in a multi-threaded environment.
 */
class JniSupport {
public:
    JniSupport(JavaVM* jvm);
    JniSupport(JNIEnv* env);

protected:
    jclass findClass(const char* className);
    JNIEnv* getThreadEnv();

protected:
    JavaVM* jvm;
};

/**
 * Attach a native thread to JNI.
 */
class JniThreadAttacher : public JniSupport {
public:
    JniThreadAttacher(JavaVM* jvm, const char* name, bool daemon);
    ~JniThreadAttacher();
};
