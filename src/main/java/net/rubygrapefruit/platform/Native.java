package net.rubygrapefruit.platform;

import net.rubygrapefruit.platform.internal.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Provides access to the native integrations. Use {@link #get(Class)} to load a particular integration.
 */
public class Native {
    private static final Object lock = new Object();
    private static boolean loaded;

    static <T extends NativeIntegration> T get(Class<T> type) {
        Platform platform = Platform.current();
        synchronized (lock) {
            if (!loaded) {
                if (!platform.isSupported()) {
                    throw new NativeException(String.format("The current platform is not supported."));
                }
                try {
                    File libFile = new File("build/binaries/" + platform.getLibraryName());
                    System.load(libFile.getCanonicalPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                int nativeVersion = NativeLibraryFunctions.getVersion();
                if (nativeVersion != NativeLibraryFunctions.VERSION) {
                    throw new NativeException(String.format(
                            "Unexpected native library version loaded. Expected %s, was %s.", nativeVersion,
                            NativeLibraryFunctions.VERSION));
                }
                loaded = true;
            }
        }
        if (platform.isPosix()) {
            if (type.equals(PosixFile.class)) {
                return type.cast(new DefaultPosixFile());
            }
            if (type.equals(Process.class)) {
                return type.cast(new DefaultProcess());
            }
            if (type.equals(TerminalAccess.class)) {
                return type.cast(new TerminfoTerminalAccess());
            }
        } else if (platform.isWindows()) {
            if (type.equals(Process.class)) {
                return type.cast(new DefaultProcess());
            }
            if (type.equals(TerminalAccess.class)) {
                return type.cast(new WindowsTerminalAccess());
            }
        }
        throw new UnsupportedOperationException(String.format("Cannot load unsupported native integration %s.",
                type.getName()));
    }

    private static class DefaultPosixFile implements PosixFile {
        @Override
        public void setMode(File file, int perms) {
            FunctionResult result = new FunctionResult();
            PosixFileFunctions.chmod(file.getPath(), perms, result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not set UNIX mode on %s: %s", file, result.getMessage()));
            }
        }

        @Override
        public int getMode(File file) {
            FunctionResult result = new FunctionResult();
            FileStat stat = new FileStat();
            PosixFileFunctions.stat(file.getPath(), stat, result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not get UNIX mode on %s: %s", file, result.getMessage()));
            }
            return stat.mode;
        }
    }

    private static class DefaultProcess implements Process {
        @Override
        public int getProcessId() throws NativeException {
            return PosixProcessFunctions.getPid();
        }
    }

    private static class TerminfoTerminalAccess implements TerminalAccess {
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
        private Color foreground;

        public DefaultTerminal(TerminalAccess.Output output) {
            this.output = output;
            stream = output == TerminalAccess.Output.Stdout ? System.out : System.err;
        }

        public void init() {
            stream.flush();
            FunctionResult result = new FunctionResult();
            TerminfoFunctions.initTerminal(output.ordinal(), result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not open terminal: %s", result.getMessage()));
            }
            Runtime.getRuntime().addShutdownHook(new Thread(){
                @Override
                public void run() {
                    reset();
                }
            });
        }

        @Override
        public TerminalSize getTerminalSize() {
            MutableTerminalSize terminalSize = new MutableTerminalSize();
            FunctionResult result = new FunctionResult();
            PosixTerminalFunctions.getTerminalSize(output.ordinal(), terminalSize, result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not get terminal size: %s", result.getMessage()));
            }
            return terminalSize;
        }

        @Override
        public Terminal foreground(Color color) {
            stream.flush();
            FunctionResult result = new FunctionResult();
            TerminfoFunctions.foreground(color.ordinal(), result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not switch foreground color: %s", result.getMessage()));
            }
            foreground = color;
            return this;
        }

        @Override
        public Terminal bold() {
            stream.flush();
            FunctionResult result = new FunctionResult();
            TerminfoFunctions.bold(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not switch to bold mode: %s", result.getMessage()));
            }
            return this;
        }

        @Override
        public Terminal normal() {
            reset();
            if (foreground != null) {
                foreground(foreground);
            }
            return this;
        }

        @Override
        public Terminal reset() {
            stream.flush();
            FunctionResult result = new FunctionResult();
            TerminfoFunctions.reset(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not reset terminal: %s", result.getMessage()));
            }
            return this;
        }
    }

    private static class WindowsTerminalAccess implements TerminalAccess {
        @Override
        public boolean isTerminal(Output output) {
            return false;
        }

        @Override
        public Terminal getTerminal(Output output) {
            throw new UnsupportedOperationException();
        }
    }
}
