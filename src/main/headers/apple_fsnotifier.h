#if defined(__APPLE__)

#include "generic.h"
#include "generic_fsnotifier.h"
#include "net_rubygrapefruit_platform_internal_jni_OsxFileEventFunctions.h"
#include <CoreServices/CoreServices.h>

using namespace std;

class Server;

static void handleEventsCallback(
    ConstFSEventStreamRef streamRef,
    void* clientCallBackInfo,
    size_t numEvents,
    void* eventPaths,
    const FSEventStreamEventFlags eventFlags[],
    const FSEventStreamEventId eventIds[]);

class Server {
public:
    Server(JNIEnv* env, jobject watcherCallback, CFArrayRef rootsToWatch, long latencyInMillis);
    ~Server();

private:
    void run();

    void handleEvents(
        size_t numEvents,
        char** eventPaths,
        const FSEventStreamEventFlags eventFlags[],
        const FSEventStreamEventId eventIds[]);
    friend void handleEventsCallback(
        ConstFSEventStreamRef streamRef,
        void* clientCallBackInfo,
        size_t numEvents,
        void* eventPaths,
        const FSEventStreamEventFlags eventFlags[],
        const FSEventStreamEventId eventIds[]);

    void handleEvent(JNIEnv* env, char* path, FSEventStreamEventFlags flags);

    // TODO: Move this to somewhere else
    JNIEnv* getThreadEnv();

    JavaVM* jvm;
    jobject watcherCallback;
    jmethodID watcherCallbackMethod;

    FSEventStreamRef watcherStream;
    thread watcherThread;
    mutex watcherThreadMutex;
    condition_variable watcherThreadStarted;
    CFRunLoopRef threadLoop;
};

#endif
