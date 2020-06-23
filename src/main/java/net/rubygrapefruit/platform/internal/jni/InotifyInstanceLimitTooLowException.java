package net.rubygrapefruit.platform.internal.jni;

public class InotifyInstanceLimitTooLowException extends InsufficientResourcesForWatchingException {
    public InotifyInstanceLimitTooLowException(String message) {
        super(message);
    }
}
