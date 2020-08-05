package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.WindowsRegistry;
import net.rubygrapefruit.platform.internal.jni.WindowsRegistryFunctions;

import java.util.ArrayList;
import java.util.List;

public class DefaultWindowsRegistry implements WindowsRegistry {
    public String getStringValue(Key key, String subkey, String valueName) throws NativeException {
        FunctionResult result = new FunctionResult();
        String value = WindowsRegistryFunctions.getStringValue(key.ordinal(), subkey, valueName, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get value '%s' of registry key '%s\\%s': %s", valueName,
                    key,
                    subkey, result.getMessage()));
        }
        if (value == null) {
            throw new MissingRegistryEntryException(String.format(
                    "Could not get value '%s' of registry key '%s\\%s' as it does not exist.", valueName, key, subkey));
        }
        return value;
    }

    public List<String> getSubkeys(Key key, String subkey) throws NativeException {
        FunctionResult result = new FunctionResult();
        ArrayList<String> subkeys = new ArrayList<String>();
        boolean found = WindowsRegistryFunctions.getSubkeys(key.ordinal(), subkey, subkeys, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not list the subkeys of registry key '%s\\%s': %s", key,
                    subkey, result.getMessage()));
        }
        if (!found) {
            throw new MissingRegistryEntryException(String.format(
                    "Could not list the subkeys of registry key '%s\\%s' as it does not exist.", key, subkey));
        }
        return subkeys;
    }

    public List<String> getValueNames(Key key, String subkey) throws NativeException {
        FunctionResult result = new FunctionResult();
        ArrayList<String> names = new ArrayList<String>();
        boolean found = WindowsRegistryFunctions.getValueNames(key.ordinal(), subkey, names, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not list the values of registry key '%s\\%s': %s", key,
                    subkey, result.getMessage()));
        }
        if (!found) {
            throw new MissingRegistryEntryException(String.format(
                    "Could not list the values of registry key '%s\\%s' as it does not exist.", key, subkey));
        }
        return names;
    }

}
