/*
 * Copyright 2012 Adam Murdoch
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.*;
import net.rubygrapefruit.platform.Process;
import net.rubygrapefruit.platform.internal.jni.NativeLibraryFunctions;
import net.rubygrapefruit.platform.internal.jni.TerminfoFunctions;

public abstract class Platform {
    private static Platform platform;

    public static Platform current() {
        synchronized (Platform.class) {
            if (platform == null) {
                String osName = getOperatingSystem().toLowerCase();
                String arch = getArchitecture();
                if (osName.contains("windows")) {
                    if (arch.equals("x86")) {
                        platform = new Window32Bit();
                    }
                    else if (arch.equals("amd64")) {
                        platform = new Window64Bit();
                    }
                } else if (osName.contains("linux")) {
                    if (arch.equals("amd64")) {
                        platform = new Linux64Bit();
                    }
                    else if (arch.equals("i386") || arch.equals("x86")) {
                        platform = new Linux32Bit();
                    }
                } else if (osName.contains("os x")) {
                    if (arch.equals("i386") || arch.equals("x86_64") || arch.equals("amd64")) {
                        platform = new OsX();
                    }
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
        throw new NativeIntegrationUnavailableException(String.format("Native integration %s is not supported for %s.", type.getSimpleName(), toString()));
    }

    public String getLibraryName() {
        throw new NativeIntegrationUnavailableException(String.format("Native integration is not available for %s.", toString()));
    }

    private static String getOperatingSystem() {
        return System.getProperty("os.name");
    }

    private static String getArchitecture() {
        return System.getProperty("os.arch");
    }

    private abstract static class Windows extends Platform {
        @Override
        public boolean isWindows() {
            return true;
        }

        @Override
        public <T extends NativeIntegration> T get(Class<T> type, NativeLibraryLoader nativeLibraryLoader) {
            if (type.equals(Process.class)) {
                return type.cast(new WrapperProcess(new DefaultProcess(), true));
            }
            if (type.equals(Terminals.class)) {
                return type.cast(new WindowsTerminals());
            }
            if (type.equals(ProcessLauncher.class)) {
                return type.cast(new WrapperProcessLauncher(new WindowsProcessLauncher(new DefaultProcessLauncher())));
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

    private static class Window32Bit extends Windows {
        @Override
        public String getLibraryName() {
            return "native-platform-windows-i386.dll";
        }
    }

    private static class Window64Bit extends Windows {
        @Override
        public String getLibraryName() {
            return "native-platform-windows-amd64.dll";
        }
    }

    private static abstract class Posix extends Platform {
        abstract String getCursesLibraryName();

        @Override
        public <T extends NativeIntegration> T get(Class<T> type, NativeLibraryLoader nativeLibraryLoader) {
            if (type.equals(PosixFile.class)) {
                return type.cast(new DefaultPosixFile());
            }
            if (type.equals(Process.class)) {
                return type.cast(new WrapperProcess(new DefaultProcess(), false));
            }
            if (type.equals(ProcessLauncher.class)) {
                return type.cast(new WrapperProcessLauncher(new DefaultProcessLauncher()));
            }
            if (type.equals(Terminals.class)) {
                nativeLibraryLoader.load(getCursesLibraryName());
                int nativeVersion = TerminfoFunctions.getVersion();
                if (nativeVersion != NativeLibraryFunctions.VERSION) {
                    throw new NativeException(String.format("Unexpected native library version loaded. Expected %s, was %s.", nativeVersion, NativeLibraryFunctions.VERSION));
                }
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

    private abstract static class Linux extends Unix {
        @Override
        public <T extends NativeIntegration> T get(Class<T> type, NativeLibraryLoader nativeLibraryLoader) {
            if (type.equals(FileSystems.class)) {
                return type.cast(new PosixFileSystems());
            }
            return super.get(type, nativeLibraryLoader);
        }
    }

    private static class Linux32Bit extends Linux {
        @Override
        public String getLibraryName() {
            return "libnative-platform-linux-i386.so";
        }

        @Override
        String getCursesLibraryName() {
            return "libnative-platform-curses-linux-i386.so";
        }
    }

    private static class Linux64Bit extends Linux {
        @Override
        public String getLibraryName() {
            return "libnative-platform-linux-amd64.so";
        }

        @Override
        String getCursesLibraryName() {
            return "libnative-platform-curses-linux-amd64.so";
        }
    }

    private static class Solaris extends Unix {
        @Override
        public String getLibraryName() {
            return "libnative-platform-solaris.so";
        }

        @Override
        String getCursesLibraryName() {
            return "libnative-platform-curses-solaris.so";
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
            return "libnative-platform-osx-universal.dylib";
        }

        @Override
        String getCursesLibraryName() {
            return "libnative-platform-curses-osx-universal.dylib";
        }
    }

    private static class Unsupported extends Platform {
    }

}
