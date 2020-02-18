#include "generic.h"
#include <mutex>
#include <thread>

using namespace std;

struct FileWatcherException : public exception {
public:
    FileWatcherException(const char* message) {
        this->message = message;
    }

    const char* what() const throw() {
        return message;
    }

private:
    const char* message;
};

class AbstractServer {
public:
    AbstractServer(JNIEnv* env);
    JNIEnv* getThreadEnv();
protected:
    // TODO Make this private
    JavaVM* jvm;
};
