package net.rubygrapefruit.platform;

/**
 * Thrown when a given integration is not available for the current machine.
 */
public class NativeIntegrationUnavailableException extends NativeException {
    public NativeIntegrationUnavailableException(String message) {
        super(message);
    }
}
