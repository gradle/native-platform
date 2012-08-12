package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.PosixFile;
import net.rubygrapefruit.platform.internal.jni.NativeLibraryFunctions;
import net.rubygrapefruit.platform.internal.jni.PosixFileFunctions;

import java.io.File;
import java.io.UnsupportedEncodingException;

public class DefaultPosixFile implements PosixFile {
    private final String characterEncoding;

    public DefaultPosixFile() {
        MutableSystemInfo systemInfo = new MutableSystemInfo();
        FunctionResult result = new FunctionResult();
        NativeLibraryFunctions.getSystemInfo(systemInfo, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not fetch system information: %s",
                    result.getMessage()));
        }
        this.characterEncoding = systemInfo.characterEncoding;
    }

    @Override
    public void setMode(File file, int perms) {
        FunctionResult result = new FunctionResult();
        PosixFileFunctions.chmod(encode(file), perms, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not set UNIX mode on %s: %s", file, result.getMessage()));
        }
    }

    @Override
    public int getMode(File file) {
        FunctionResult result = new FunctionResult();
        FileStat stat = new FileStat();
        PosixFileFunctions.stat(encode(file), stat, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get UNIX mode on %s: %s", file, result.getMessage()));
        }
        return stat.mode;
    }

    private byte[] encode(File file) {
        byte[] encodedName;
        try {
            encodedName = file.getPath().getBytes(characterEncoding);
        } catch (UnsupportedEncodingException e) {
            throw new NativeException(String.format("Could not encode path for file '%s' using encoding %s.", file.getName(), characterEncoding));
        }
        byte[] buffer = new byte[encodedName.length + 1];
        System.arraycopy(encodedName, 0, buffer, 0, encodedName.length);
        buffer[buffer.length - 1] = 0;
        return buffer;
    }
}
