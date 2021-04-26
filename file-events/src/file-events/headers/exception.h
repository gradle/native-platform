#pragma once

#include <exception>
#include <sstream>
#include <string>

#include "jni_support.h"

using namespace std;

inline string createMessage(const string& message, const u16string& path) {
    stringstream ss;
    ss << message;
    ss << ": ";
    ss << utf16ToUtf8String(path);
    return ss.str();
}

inline string createMessage(const string& message, int errorCode) {
    stringstream ss;
    ss << message;
    ss << ", error = ";
    ss << errorCode;
    return ss.str();
}

inline string createMessage(const string& message, const u16string& path, int errorCode) {
    stringstream ss;
    ss << message;
    ss << ", error = ";
    ss << errorCode;
    ss << ": ";
    ss << utf16ToUtf8String(path);
    return ss.str();
}

struct FileWatcherException : public runtime_error {
public:
    FileWatcherException(const string& message, const u16string& path, int errorCode)
        : runtime_error(createMessage(message, path, errorCode)) {
    }

    FileWatcherException(const string& message, const u16string& path)
        : runtime_error(createMessage(message, path)) {
    }

    FileWatcherException(const string& message, int errorCode)
        : runtime_error(createMessage(message, errorCode)) {
    }

    FileWatcherException(const string& message)
        : runtime_error(message) {
    }
};
