package net.rubygrapefruit.platform.internal.jni;

import java.util.Collection;

/**
 * A {@link InotifyInstanceLimitTooLowException} is thrown by {@link net.rubygrapefruit.platform.file.FileWatcher#startWatching(Collection)}
 * when the inotify watches count is too low.
 */
@SuppressWarnings("unused") // Thrown from the native side
public class InotifyWatchesLimitTooLowException extends InsufficientResourcesForWatchingException {
    public InotifyWatchesLimitTooLowException(String message) {
        super(message);
    }
}
