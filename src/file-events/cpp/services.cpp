#include "logging.h"
#include "generic_fsnotifier.h"

JniConstants* jniConstants;
Logging* logging;

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* jvm, void*) {
    jniConstants = new JniConstants(jvm);
    logging = new Logging(jvm);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM*, void*) {
    delete logging;
    delete jniConstants;
}
