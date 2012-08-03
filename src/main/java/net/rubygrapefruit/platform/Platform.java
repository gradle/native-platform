package net.rubygrapefruit.platform;

import net.rubygrapefruit.platform.internal.*;

import java.io.File;
import java.io.IOException;

/**
 * Provides access to the native integrations. Use {@link #get(Class)} to load a particular integration.
 */
public class Platform {
    private static final Object lock = new Object();
    private static boolean loaded;

    static <T extends NativeIntegration> T get(Class<T> type) {
        synchronized (lock) {
            if (!loaded) {
                System.setProperty("java.library.path", new File("build/binaries").getAbsolutePath());
                try {
                    File libFile = new File("build/binaries/libnative-platform.dylib");
                    if (!libFile.isFile()) {
                        libFile = new File("build/binaries/libnative-platform.so");
                    }
                    System.load(libFile.getCanonicalPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                loaded = true;
            }
        }
        if (type.equals(PosixFile.class)) {
            return type.cast(new DefaultPosixFile());
        }
        if (type.equals(Process.class)) {
            return type.cast(new DefaultProcess());
        }
        if (type.equals(TerminalAccess.class)) {
            return type.cast(new DefaultTerminalAccess());
        }
        throw new UnsupportedOperationException(String.format("Cannot load unknown native integration %s.",
                type.getName()));
    }

    private static class DefaultPosixFile implements PosixFile {
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
    }

    private static class DefaultProcess implements Process {
        @Override
        public int getPid() throws NativeException {
            return PosixProcessFunctions.getPid();
        }
    }

    private static class DefaultTerminalAccess implements TerminalAccess {
        @Override
        public boolean isTerminal(Output output) {
            return PosixTerminalFunctions.isatty(output.ordinal());
        }

        @Override
        public Terminal getTerminal(Output output) {
            if (!isTerminal(output)) {
                throw new NativeException(String.format("%s is not attached to a terminal.", output));
            }
            return new DefaultTerminal(output);
        }
    }

    private static class DefaultTerminal implements Terminal {
        private final TerminalAccess.Output output;

        public DefaultTerminal(TerminalAccess.Output output) {
            this.output = output;
        }

        @Override
        public TerminalSize getTerminalSize() {
            MutableTerminalSize terminalSize = new MutableTerminalSize();
            FunctionResult result = new FunctionResult();
            PosixTerminalFunctions.getTerminalSize(output.ordinal(), terminalSize, result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not get terminal size. Errno is %d.",
                        result.getErrno()));
            }
            return terminalSize;
        }
    }
}
