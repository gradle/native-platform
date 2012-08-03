package net.rubygrapefruit.platform.internal;

public class FunctionResult {
    int errno;

    void failed(int errno) {
        this.errno = errno;
    }

    public boolean isFailed() {
        return errno != 0;
    }

    public int getErrno() {
        return errno;
    }
}
