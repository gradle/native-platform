package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.PosixFile;
import net.rubygrapefruit.platform.internal.jni.PosixFileFunctions;

import java.io.File;
import java.io.UnsupportedEncodingException;

public class DefaultPosixFile implements PosixFile {
    private final String characterEncoding;

    public DefaultPosixFile() {
        DefaultSystemInfo systemInfo = new DefaultSystemInfo();
        this.characterEncoding = systemInfo.getCharacterEncoding();
    }

    public void setMode(File file, int perms) {
        FunctionResult result = new FunctionResult();
        PosixFileFunctions.chmod(encode(file), perms, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not set UNIX mode on %s: %s", file, result.getMessage()));
        }
    }

    public int getMode(File file) {
        FunctionResult result = new FunctionResult();
        FileStat stat = new FileStat();
        PosixFileFunctions.stat(encode(file), stat, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get UNIX mode on %s: %s", file, result.getMessage()));
        }
        return stat.mode;
    }

    @Override
    public String readLink(File link) throws NativeException {
        FunctionResult result = new FunctionResult();
        byte[] encodedContents = PosixFileFunctions.readlink(encode(link), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not read symlink %s: %s", link, result.getMessage()));
        }
        return decode(encodedContents);
    }

    @Override
    public void symlink(File link, String contents) throws NativeException {
        FunctionResult result = new FunctionResult();
        PosixFileFunctions.symlink(encode(link), encode(contents), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not create symlink %s: %s", link, result.getMessage()));
        }
    }

    private String decode(byte[] path) {
        try {
            return new String(path, 0, path.length, characterEncoding);
        } catch (UnsupportedEncodingException e) {
            throw new NativeException(String.format("Could not decode path using encoding %s.", characterEncoding), e);
        }
    }

    private byte[] encode(File file) {
        return encode(file.getPath());
    }

    private byte[] encode(String path) {
        byte[] encodedName;
        try {
            encodedName = path.getBytes(characterEncoding);
        } catch (UnsupportedEncodingException e) {
            throw new NativeException(String.format("Could not encode path '%s' using encoding %s.", path, characterEncoding), e);
        }
        byte[] buffer = new byte[encodedName.length + 1];
        System.arraycopy(encodedName, 0, buffer, 0, encodedName.length);
        buffer[buffer.length - 1] = 0;
        return buffer;
    }
}
