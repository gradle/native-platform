#include "logging.h"
#include "generic_fsnotifier.h"

BaseJniConstants* baseJniConstants;
NativePlatformJniConstants* nativePlatformJniConstants;
Logging* logging;

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* jvm, void*) {
    baseJniConstants = new BaseJniConstants(jvm);
    nativePlatformJniConstants = new NativePlatformJniConstants(jvm);
    logging = new Logging(jvm);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM*, void*) {
    delete logging;
    delete nativePlatformJniConstants;
    delete baseJniConstants;
}
