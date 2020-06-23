package net.rubygrapefruit.platform.internal.jni;

public class InotifyWatchesLimitTooLowException extends InsufficientResourcesForWatchingException {
    public InotifyWatchesLimitTooLowException(String message) {
        super(message);
    }
}
