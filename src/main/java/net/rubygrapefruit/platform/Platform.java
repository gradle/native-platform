package net.rubygrapefruit.platform;

import net.rubygrapefruit.platform.internal.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

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
                int nativeVersion = NativeLibraryFunctions.getVersion();
                if (nativeVersion != NativeLibraryFunctions.VERSION) {
                    throw new NativeException(String.format("Unexpected native library version loaded. Expected %s, was %s.", nativeVersion, NativeLibraryFunctions.VERSION));
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
        private static Output currentlyOpen;

        @Override
        public boolean isTerminal(Output output) {
            return PosixTerminalFunctions.isatty(output.ordinal());
        }

        @Override
        public Terminal getTerminal(Output output) {
            if (currentlyOpen != null) {
                throw new UnsupportedOperationException("Currently only one output can be used as a terminal.");
            }

            DefaultTerminal terminal = new DefaultTerminal(output);
            terminal.init();

            currentlyOpen = output;
            return terminal;
        }
    }

    private static class DefaultTerminal implements Terminal {
        private final TerminalAccess.Output output;
        private final PrintStream stream;

        public DefaultTerminal(TerminalAccess.Output output) {
            this.output = output;
            stream = output == TerminalAccess.Output.Stdout ? System.out : System.err;
        }

        public void init() {
            stream.flush();
            FunctionResult result = new FunctionResult();
            TerminfoFunctions.initTerminal(output.ordinal(), result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not open terminal. Errno is %d.",
                        result.getErrno()));
            }
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

        @Override
        public Terminal bold() {
            stream.flush();
            FunctionResult result = new FunctionResult();
            TerminfoFunctions.bold(output.ordinal(), result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not switch to bold mode. Errno is %d.",
                        result.getErrno()));
            }
            return this;
        }

        @Override
        public Terminal bold(String output) {
            bold();
            stream.print(output);
            normal();
            return this;
        }

        @Override
        public Terminal normal() {
            stream.flush();
            FunctionResult result = new FunctionResult();
            TerminfoFunctions.normal(output.ordinal(), result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not switch to normal mode. Errno is %d.",
                        result.getErrno()));
            }
            return this;
        }
    }
}
