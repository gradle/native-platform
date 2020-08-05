package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.NativeException;

public class InsufficientResourcesForWatchingException extends NativeException {
    public InsufficientResourcesForWatchingException(String message) {
        super(message);
    }
}
