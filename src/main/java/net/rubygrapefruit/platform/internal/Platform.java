package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.*;
import net.rubygrapefruit.platform.Process;
import net.rubygrapefruit.platform.internal.jni.NativeLibraryFunctions;

public abstract class Platform {
    private static Platform platform;

    public static Platform current() {
        synchronized (Platform.class) {
            if (platform == null) {
                String osName = getOperatingSystem().toLowerCase();
                String arch = getArchitecture();
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

    public boolean isWindows() {
        return false;
    }

    public <T extends NativeIntegration> T get(Class<T> type) {
        throw new NativeIntegrationUnavailableException(String.format("Native integration %s is not supported on this operating system (%s %s)",
                type.getSimpleName(), getOperatingSystem(), getArchitecture()));
    }

    public abstract String getLibraryName() throws NativeIntegrationUnavailableException;

    private static class Windows extends Platform {
        @Override
        public boolean isWindows() {
            return true;
        }

        @Override
        public String getLibraryName() {
            return "native-platform-win32.dll";
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
            if (getArchitecture().equals("amd64")) {
                return "libnative-platform-linux-amd64.so";
            }
            if (getArchitecture().equals("i386") || getArchitecture().equals("x86")) {
                return "libnative-platform-linux-i386.so";
            }
            throw new NativeIntegrationUnavailableException(String.format(
                    "Native integration is not available for this architecture (%s) on Linux.", getArchitecture()));
        }
    }

    private static String getArchitecture() {
        return System.getProperty("os.arch");
    }

    private static class Solaris extends Unix {
        @Override
        public String getLibraryName() {
            return "libnative-platform-solaris.so";
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
            return "libnative-platform-osx-universal.dylib";
        }
    }

    private static class Unsupported extends Platform {
        public String getLibraryName() {
            throw new NativeIntegrationUnavailableException(String.format(
                    "Native integration is not available for this operating system (%s %s)", getOperatingSystem(),
                    getArchitecture()));
        }
    }

    private static String getOperatingSystem() {
        return System.getProperty("os.name");
    }
}
