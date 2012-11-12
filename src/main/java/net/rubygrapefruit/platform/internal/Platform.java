package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.*;
import net.rubygrapefruit.platform.Process;

public abstract class Platform {
    private static Platform platform;

    public static Platform current() {
        synchronized (Platform.class) {
            if (platform == null) {
                String osName = getOperatingSystem().toLowerCase();
                if (osName.contains("windows")) {
                    platform = new Windows();
                } else if (osName.contains("linux")) {
                    platform = new Linux();
                } else if (osName.contains("os x")) {
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

    @Override
    public String toString() {
        return String.format("%s %s", getOperatingSystem(), getArchitecture());
    }

    public <T extends NativeIntegration> T get(Class<T> type, NativeLibraryLoader nativeLibraryLoader) {
        throw new NativeIntegrationUnavailableException(String.format("Native integration %s is not supported for the current operating system (%s)",
                type.getSimpleName(), toString()));
    }

    public String getLibraryName() {
        throw new NativeIntegrationUnavailableException(String.format(
                "Native integration is not available for the current operating system (%s)", toString()));
    }

    private static String getOperatingSystem() {
        return System.getProperty("os.name");
    }

    private static String getArchitecture() {
        return System.getProperty("os.arch");
    }

    private static class Windows extends Platform {
        @Override
        public boolean isWindows() {
            return true;
        }

        @Override
        public String getLibraryName() {
            if (getArchitecture().equals("x86")) {
                return "native-platform-windows-i386.dll";
            }
            if (getArchitecture().equals("amd64")) {
                return "native-platform-windows-amd64.dll";
            }
            return super.getLibraryName();
        }

        @Override
        public <T extends NativeIntegration> T get(Class<T> type, NativeLibraryLoader nativeLibraryLoader) {
            if (type.equals(Process.class)) {
                return type.cast(new DefaultProcess());
            }
            if (type.equals(Terminals.class)) {
                return type.cast(new WindowsTerminals());
            }
            if (type.equals(SystemInfo.class)) {
                return type.cast(new DefaultSystemInfo());
            }
            if (type.equals(FileSystems.class)) {
                return type.cast(new PosixFileSystems());
            }
            return super.get(type, nativeLibraryLoader);
        }
    }

    private static abstract class Posix extends Platform {
        @Override
        public <T extends NativeIntegration> T get(Class<T> type, NativeLibraryLoader nativeLibraryLoader) {
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
                return type.cast(new DefaultSystemInfo());
            }
            return super.get(type, nativeLibraryLoader);
        }
    }

    private abstract static class Unix extends Posix {
    }

    private static class Linux extends Unix {
        @Override
        public <T extends NativeIntegration> T get(Class<T> type, NativeLibraryLoader nativeLibraryLoader) {
            if (type.equals(FileSystems.class)) {
                return type.cast(new PosixFileSystems());
            }
            return super.get(type, nativeLibraryLoader);
        }

        @Override
        public String getLibraryName() {
            if (getArchitecture().equals("amd64")) {
                return "libnative-platform-linux-amd64.so";
            }
            if (getArchitecture().equals("i386") || getArchitecture().equals("x86")) {
                return "libnative-platform-linux-i386.so";
            }
            return super.getLibraryName();
        }
    }


    private static class Solaris extends Unix {
        @Override
        public String getLibraryName() {
            return "libnative-platform-solaris.so";
        }
    }

    private static class OsX extends Posix {
        @Override
        public <T extends NativeIntegration> T get(Class<T> type, NativeLibraryLoader nativeLibraryLoader) {
            if (type.equals(FileSystems.class)) {
                return type.cast(new PosixFileSystems());
            }
            return super.get(type, nativeLibraryLoader);
        }

        @Override
        public String getLibraryName() {
            String arch = getArchitecture();
            if (arch.equals("i386") || arch.equals("x86_64") || arch.equals("amd64")) {
                return "libnative-platform-osx-universal.dylib";
            }
            return super.getLibraryName();
        }
    }

    private static class Unsupported extends Platform {
    }

}
