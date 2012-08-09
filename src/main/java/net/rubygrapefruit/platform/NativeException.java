package net.rubygrapefruit.platform;

public class NativeException extends RuntimeException {
    public NativeException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public NativeException(String message) {
        super(message);
    }
}
