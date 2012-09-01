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
            return "native-win32.dll";
        }

        @Override
        public <T extends NativeIntegration> T get(Class<T> type) {
            if (type.equals(net.rubygrapefruit.platform.Process.class)) {
                return type.cast(new DefaultProcess());
            }
            if (type.equals(Terminals.class)) {
                return type.cast(new WindowsTerminals());
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
            if (type.equals(Terminals.class)) {
                return type.cast(new TerminfoTerminals());
            }
            if (type.equals(SystemInfo.class)) {
                MutableSystemInfo systemInfo = new MutableSystemInfo();
                FunctionResult result = new FunctionResult();
                NativeLibraryFunctions.getSystemInfo(systemInfo, result);
                if (result.isFailed()) {
                    throw new NativeException(String.format("Could not fetch system information: %s",
                            result.getMessage()));
                }
                return type.cast(systemInfo);
            }
            return super.get(type);
        }
    }

    private abstract static class Unix extends Posix {
    }

    private static class Linux extends Unix {
        @Override
        public <T extends NativeIntegration> T get(Class<T> type) {
            if (type.equals(FileSystems.class)) {
                return type.cast(new PosixFileSystems());
            }
            return super.get(type);
        }

        @Override
        public String getLibraryName() {
            if (System.getProperty("os.arch").equals("amd64")) {
                return "libnative-linux-amd64.so";
            }
            return "libnative-linux-i386.so";
        }
    }

    private static class Solaris extends Unix {
        @Override
        public String getLibraryName() {
            return "libnative-solaris.so";
        }
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
            return "libnative-osx-universal.dylib";
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
