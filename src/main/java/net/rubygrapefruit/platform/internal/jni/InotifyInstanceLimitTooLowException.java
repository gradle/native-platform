package net.rubygrapefruit.platform.internal.jni;

/**
 * A {@link InotifyInstanceLimitTooLowException} is thrown by {@link AbstractFileEventFunctions.AbstractWatcherBuilder#start()}
 * when the inotify instance count is too low.
 */
public class InotifyInstanceLimitTooLowException extends InsufficientResourcesForWatchingException {
    public InotifyInstanceLimitTooLowException(String message) {
        super(message);
    }
}
