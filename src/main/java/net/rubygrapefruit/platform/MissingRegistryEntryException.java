package net.rubygrapefruit.platform;

/**
 * Thrown when attempting to query an unknown registry key or value.
 */
public class MissingRegistryEntryException extends NativeException {
    public MissingRegistryEntryException(String message) {
        super(message);
    }
}
