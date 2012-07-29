package net.rubygrapefruit.platform;

import java.io.File;

public interface UnixFileMode extends NativeIntegration {
    void setMode(File path, int perms) throws NativeException;

    int getMode(File path) throws NativeException;
}
