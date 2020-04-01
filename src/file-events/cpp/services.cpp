#include "logging.h"
#include "generic_fsnotifier.h"

JniConstants* jniConstants;
NativeConstants* nativeContants;
Logging* logging;

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* jvm, void*) {
    jniConstants = new JniConstants(jvm);
    nativeContants = new NativeConstants(jvm);
    logging = new Logging(jvm);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM*, void*) {
    delete logging;
    delete nativeContants;
    delete jniConstants;
}
