package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.*;
import net.rubygrapefruit.platform.Process;
import net.rubygrapefruit.platform.internal.jni.NativeLibraryFunctions;

public abstract class Platform {
    private static Platform platform;

    public static Platform current() {
        synchronized (Platform.class) {
            if (platform == null) {
                String osName = System.getProperty("os.name").toLowerCase();
                String arch = System.getProperty("os.arch");
                if (osName.contains("windows")) {
                    platform = new Windows();
                } else if (osName.contains("linux")) {
                    platform = new Linux();
                } else if (osName.contains("os x") && (arch.equals("i386") || arch.equals("x86_64"))) {
                    platform = new OsX();
                } else if (osName.contains("sunos")) {
                    platform = new Solaris();
                } else {
                    platform = new Unsupported();
                }
            }
            return platform;
        }
    }

    public boolean isSupported() {
        return true;
    }

    public boolean isWindows() {
        return false;
    }

    public <T extends NativeIntegration> T get(Class<T> type) {
        return null;
    }

    public abstract String getLibraryName();

    private static class Windows extends Platform {
        @Override
        public boolean isWindows() {
            return true;
        }

        @Override
        public String getLibraryName() {
            return "native-platform.dll";
        }

        @Override
        public <T extends NativeIntegration> T get(Class<T> type) {
            if (type.equals(net.rubygrapefruit.platform.Process.class)) {
                return type.cast(new DefaultProcess());
            }
            if (type.equals(TerminalAccess.class)) {
                return type.cast(new WindowsTerminalAccess());
            }
            return super.get(type);
        }
    }

    private static abstract class Posix extends Platform {
        @Override
        public <T extends NativeIntegration> T get(Class<T> type) {
            if (type.equals(PosixFile.class)) {
                return type.cast(new DefaultPosixFile());
            }
            if (type.equals(Process.class)) {
                return type.cast(new DefaultProcess());
            }
            if (type.equals(TerminalAccess.class)) {
                return type.cast(new TerminfoTerminalAccess());
            }
            if (type.equals(SystemInfo.class)) {
                MutableSystemInfo systemInfo = new MutableSystemInfo();
                FunctionResult result = new FunctionResult();
                NativeLibraryFunctions.getSystemInfo(systemInfo, result);
                if (result.isFailed()) {
                    throw new NativeException(String.format("Could not fetch system information: %s",
                            result.getMessage()));
                }
                System.out.println("=> CHARACTER ENCODING: " + systemInfo.characterEncoding);
                return type.cast(systemInfo);
            }
            return super.get(type);
        }
    }

    private static class Unix extends Posix {
        @Override
        public String getLibraryName() {
            return "libnative-platform.so";
        }
    }

    private static class Linux extends Unix {
    }

    private static class Solaris extends Unix {
    }

    private static class OsX extends Posix {
        @Override
        public <T extends NativeIntegration> T get(Class<T> type) {
            if (type.equals(FileSystems.class)) {
                return type.cast(new PosixFileSystems());
            }
            return super.get(type);
        }

        @Override
        public String getLibraryName() {
            return "libnative-platform.dylib";
        }
    }

    private static class Unsupported extends Platform {
        @Override
        public boolean isSupported() {
            return false;
        }

        public String getLibraryName() {
            throw new UnsupportedOperationException();
        }
    }
}
