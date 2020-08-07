package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.internal.FunctionResult;

import java.util.List;

public class WindowsRegistryFunctions {
    // Returns null for unknown key or value
    public static native String getStringValue(int key, String subkey, String value, FunctionResult result);

    // Returns false for unknown key
    public static native boolean getSubkeys(int key, String subkey, List<String> subkeys, FunctionResult result);

    // Returns false for unknown key
    public static native boolean getValueNames(int key, String subkey, List<String> names, FunctionResult result);
}
