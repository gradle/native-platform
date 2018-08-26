package net.rubygrapefruit.platform;

import java.util.List;

/**
 * Provides access to the Windows registry.
 */
@ThreadSafe
public interface WindowsRegistry extends NativeIntegration {
    enum Key {
        HKEY_LOCAL_MACHINE, HKEY_CURRENT_USER
    }

    /**
     * Returns a registry key value as a String.
     *
     * @throws NativeException               On failure.
     * @throws MissingRegistryEntryException When the requested key or value does not exist.
     */
    String getStringValue(Key key, String subkey, String value) throws NativeException;

    /**
     * Lists the subkeys of a registry key.
     *
     * @throws NativeException               On failure.
     * @throws MissingRegistryEntryException When the requested key does not exist.
     */
    List<String> getSubkeys(Key key, String subkey) throws NativeException;

    /**
     * Lists the value names of a registry key.
     *
     * @throws NativeException               On failure.
     * @throws MissingRegistryEntryException When the requested key does not exist.
     */
    List<String> getValueNames(Key key, String subkey) throws NativeException;

}
