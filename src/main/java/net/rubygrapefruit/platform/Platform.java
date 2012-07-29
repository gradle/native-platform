package net.rubygrapefruit.platform;

import net.rubygrapefruit.platform.internal.FileStat;
import net.rubygrapefruit.platform.internal.FunctionResult;
import net.rubygrapefruit.platform.internal.PosixFileFunctions;

import java.io.File;
import java.io.IOException;

public class Platform {
    private static final Object lock = new Object();
    private static boolean loaded;

    static <T extends NativeIntegration> T get(Class<T> type) {
        synchronized (lock) {
            if (!loaded) {
                System.setProperty("java.library.path", new File("build/binaries").getAbsolutePath());
                try {
                    System.load(new File("build/binaries/libnative-platform.dylib").getCanonicalPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                loaded = true;
            }
        }
        return type.cast(new UnixFileMode() {
            @Override
            public void setMode(File file, int perms) {
                FunctionResult result = new FunctionResult();
                PosixFileFunctions.chmod(file.getPath(), perms, result);
                if (result.isFailed()) {
                    throw new NativeException(String.format("Could not set UNIX mode on %s. Errno is %d.", file, result.getErrno()));
                }
            }

            @Override
            public int getMode(File file) {
                FunctionResult result = new FunctionResult();
                FileStat stat = new FileStat();
                PosixFileFunctions.stat(file.getPath(), stat, result);
                if (result.isFailed()) {
                    throw new NativeException(String.format("Could not get UNIX mode on %s. Errno is %d.", file, result.getErrno()));
                }
                return stat.mode;
            }
        });
    }
}
