package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.*;
import net.rubygrapefruit.platform.internal.jni.PosixProcessFunctions;

public class DefaultProcess implements net.rubygrapefruit.platform.Process {
    public int getProcessId() throws NativeException {
        return PosixProcessFunctions.getPid();
    }
}
