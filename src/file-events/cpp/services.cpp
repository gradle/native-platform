#include "logging.h"
#include "generic_fsnotifier.h"
#include "linux_fsnotifier.h"

BaseJniConstants* baseJniConstants;
NativePlatformJniConstants* nativePlatformJniConstants;
#ifdef __linux__
LinuxJniConstants* linuxJniConstants;
#endif
Logging* logging;

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* jvm, void*) {
    baseJniConstants = new BaseJniConstants(jvm);
    nativePlatformJniConstants = new NativePlatformJniConstants(jvm);
    logging = new Logging(jvm);
#ifdef __linux__
    linuxJniConstants = new LinuxJniConstants(jvm);
#endif
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM*, void*) {
#ifdef __linux__
    delete linuxJniConstants;
#endif
    delete logging;
    delete nativePlatformJniConstants;
    delete baseJniConstants;
}
