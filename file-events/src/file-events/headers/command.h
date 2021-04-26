#pragma once

#include <chrono>
#include <condition_variable>
#include <functional>
#include <mutex>

#include "exception.h"

using namespace std;

class Command {
public:
    Command(function<bool()> work)
        : work(work) {
    }

    bool execute(long timeout, function<void(Command*)> scheduleWithRunLoop) {
        unique_lock<mutex> lock(executionMutex);
        scheduleWithRunLoop(this);
        auto status = executed.wait_for(lock, chrono::milliseconds(timeout));
        if (status == cv_status::timeout) {
            throw FileWatcherException("Execution timed out");
        } else if (failure) {
            rethrow_exception(failure);
        } else {
            return result;
        }
    }

    void executeInsideRunLoop() {
        try {
            result = work();
        } catch (const exception&) {
            failure = current_exception();
        }
        unique_lock<mutex> lock(executionMutex);
        executed.notify_all();
    }

private:
    function<bool()> work;
    mutex executionMutex;
    condition_variable executed;
    bool result;
    exception_ptr failure;
};
