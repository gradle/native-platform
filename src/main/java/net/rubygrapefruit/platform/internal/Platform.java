package net.rubygrapefruit.platform.internal;

public abstract class Platform {
    private static Platform platform;

    public static Platform current() {
        synchronized (Platform.class) {
            if (platform == null) {
                String osName = System.getProperty("os.name").toLowerCase();
                if (osName.contains("windows")) {
                    platform = new Windows();
                } else if (osName.contains("linux")) {
                    platform = new Linux();
                } else if (osName.contains("os x")) {
                    platform = new OsX();
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

    public boolean isPosix() {
        return false;
    }

    public boolean isWindows() {
        return false;
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
    }

    private static abstract class Posix extends Platform {
        @Override
        public boolean isPosix() {
            return true;
        }
    }

    private static class Linux extends Posix {
        @Override
        public String getLibraryName() {
            return "libnative-platform.so";
        }
    }

    private static class OsX extends Posix {
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
