package net.rubygrapefruit.platform.internal;

public class FunctionResult {
    String message;
    int errno;

    void failed(String message, int errno) {
        this.message = message;
        this.errno = errno;
    }

    void failed(String message) {
        this.message = message;
    }

    public boolean isFailed() {
        return message != null;
    }

    public String getMessage() {
        if (errno != 0) {
            return String.format("%s (errno %d)", message, errno);
        }
        return message;
    }
}
