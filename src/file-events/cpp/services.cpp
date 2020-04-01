#include "logging.h"
#include "generic_fsnotifier.h"

JniConstants* jniConstants;
NativeConstants* nativeConstants;
Logging* logging;

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* jvm, void*) {
    jniConstants = new JniConstants(jvm);
    nativeConstants = new NativeConstants(jvm);
    logging = new Logging(jvm);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM*, void*) {
    delete logging;
    delete nativeConstants;
    delete jniConstants;
}
