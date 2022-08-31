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

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.NativeIntegrationUnavailableException;
import net.rubygrapefruit.platform.Process;
import net.rubygrapefruit.platform.ProcessLauncher;
import net.rubygrapefruit.platform.SystemInfo;
import net.rubygrapefruit.platform.WindowsRegistry;
import net.rubygrapefruit.platform.file.FileSystems;
import net.rubygrapefruit.platform.file.Files;
import net.rubygrapefruit.platform.file.PosixFiles;
import net.rubygrapefruit.platform.file.WindowsFiles;
import net.rubygrapefruit.platform.internal.jni.NativeVersion;
import net.rubygrapefruit.platform.internal.jni.PosixTypeFunctions;
import net.rubygrapefruit.platform.internal.jni.TerminfoFunctions;
import net.rubygrapefruit.platform.memory.Memory;
import net.rubygrapefruit.platform.memory.OsxMemory;
import net.rubygrapefruit.platform.terminal.Terminals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
                    } else if (arch.equals("amd64")) {
                        platform = new Window64Bit();
                    } else if (arch.equals("aarch64")) {
                        platform = new WindowAarch64();
                    }
                } else if (osName.contains("linux")) {
                    if (arch.equals("amd64") || arch.equals("x86_64")) {
                        platform = new Linux64Bit();
                    } else if (arch.equals("i386") || arch.equals("x86")) {
                        platform = new Linux32Bit();
                    } else if (arch.equals("aarch64")) {
                        platform = new LinuxAarch64();
                    }
                } else if (osName.contains("os x") || osName.contains("darwin")) {
                    if (arch.equals("i386")) {
                        platform = new MacOs32Bit();
                    } else if (arch.equals("x86_64") || arch.equals("amd64") || arch.equals("universal")) {
                        platform = new MacOs64Bit();
                    } else if (arch.equals("aarch64")) {
                        platform = new MacOsAarch64();
                    }
                } else if (osName.contains("freebsd")) {
                    if (arch.equals("amd64")) {
                        platform = new FreeBSD64Bit();
                    } else if (arch.equals("i386") || arch.equals("x86")) {
                        platform = new FreeBSD32Bit();
                    }
                }
                if (platform == null) {
                    platform = new Unsupported();
                }
            }
            return platform;
        }
    }

    public boolean isLinux() {
        return false;
    }

    public boolean isMacOs() {
        return false;
    }

    public boolean isFreeBSD() {
        return false;
    }

    public boolean isWindows() {
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s %s", getOperatingSystem(), getArchitecture());
    }

    public <T extends NativeIntegration> Class<? extends T> canonicalise(Class<T> type) {
        return type;
    }

    public <T extends NativeIntegration> T get(Class<T> type, NativeLibraryLoader nativeLibraryLoader) {
        throw new NativeIntegrationUnavailableException(String.format("Native integration %s is not supported for %s.",
                type.getSimpleName(), toString()));
    }

    public String getLibraryName() {
        throw new NativeIntegrationUnavailableException(String.format("Native integration is not available for %s.",
                toString()));
    }

    public List<String> getLibraryVariants() {
        return Collections.singletonList(getId());
    }

    public abstract String getId();

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
        public String getLibraryName() {
            return "native-platform.dll";
        }

        @Override
        public List<String> getLibraryVariants() {
            return Arrays.asList(getId(), getId() + "-min");
        }

        @Override
        public <T extends NativeIntegration> Class<? extends T> canonicalise(Class<T> type) {
            if (type.equals(Files.class)) {
                return WindowsFiles.class.asSubclass(type);
            }
            return super.canonicalise(type);
        }

        @Override
        public <T extends NativeIntegration> T get(Class<T> type, NativeLibraryLoader nativeLibraryLoader) {
            if (type.equals(WindowsFiles.class)) {
                return type.cast(new DefaultWindowsFiles());
            }
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
            if (type.equals(WindowsRegistry.class)) {
                return type.cast(new DefaultWindowsRegistry());
            }
            return super.get(type, nativeLibraryLoader);
        }
    }

    private static class Window32Bit extends Windows {
        @Override
        public String getId() {
            return "windows-i386";
        }
    }

    private static class Window64Bit extends Windows {
        @Override
        public String getId() {
            return "windows-amd64";
        }
    }

    private static class WindowAarch64 extends Windows {
        @Override
        public String getId() {
            return "windows-aarch64";
        }
    }

    private static abstract class Posix extends Platform {
        abstract String getCursesLibraryName();

        List<String> getCursesVariants() {
            return getLibraryVariants();
        }

        @Override
        public <T extends NativeIntegration> Class<? extends T> canonicalise(Class<T> type) {
            if (type.equals(Files.class)) {
                return PosixFiles.class.asSubclass(type);
            }
            return super.canonicalise(type);
        }

        @Override
        public <T extends NativeIntegration> T get(Class<T> type, NativeLibraryLoader nativeLibraryLoader) {
            if (type.equals(PosixFiles.class)) {
                return type.cast(new DefaultPosixFiles());
            }
            if (type.equals(Process.class)) {
                return type.cast(new WrapperProcess(new DefaultProcess(), false));
            }
            if (type.equals(ProcessLauncher.class)) {
                return type.cast(new WrapperProcessLauncher(new DefaultProcessLauncher()));
            }
            if (type.equals(Terminals.class)) {
                nativeLibraryLoader.load(getCursesLibraryName(), getCursesVariants());
                String nativeVersion = TerminfoFunctions.getVersion();
                if (!nativeVersion.equals(NativeVersion.VERSION)) {
                    throw new NativeException(String.format(
                            "Unexpected native library version loaded. Expected %s, was %s.", nativeVersion,
                        NativeVersion.VERSION));
                }
                return type.cast(new PosixTerminals());
            }
            if (type.equals(SystemInfo.class)) {
                return type.cast(new DefaultSystemInfo());
            }
            if (type.equals(FileSystems.class)) {
                return type.cast(new PosixFileSystems());
            }
            if (type.equals(MutableTypeInfo.class)) {
                MutableTypeInfo typeInfo = new MutableTypeInfo();
                PosixTypeFunctions.getNativeTypeInfo(typeInfo);
                return type.cast(typeInfo);
            }
            return super.get(type, nativeLibraryLoader);
        }
    }

    private abstract static class Unix extends Posix {
        @Override
        public String getLibraryName() {
            return "libnative-platform.so";
        }

        @Override
        String getCursesLibraryName() {
            return "libnative-platform-curses.so";
        }
    }

    private abstract static class Linux extends Unix {
        @Override
        List<String> getCursesVariants() {
            return Arrays.asList(getId() + "-ncurses5", getId() + "-ncurses6");
        }

        @Override
        public boolean isLinux() {
            return true;
        }
    }

    private static class Linux32Bit extends Linux {
        @Override
        public String getId() {
            return "linux-i386";
        }
    }

    private static class Linux64Bit extends Linux {
        @Override
        public String getId() {
            return "linux-amd64";
        }
    }

    private static class LinuxAarch64 extends Linux {
        @Override
        public String getId() {
            return "linux-aarch64";
        }
    }

    private abstract static class FreeBSD extends Unix {
        @Override
        public List<String> getLibraryVariants() {
            return Arrays.asList(getId() + "-libcpp", getId() + "-libstdcpp");
        }

        @Override
        public boolean isFreeBSD() {
            return true;
        }
    }

    private static class FreeBSD32Bit extends FreeBSD {
        @Override
        public String getId() {
            return "freebsd-i386";
        }
    }

    private static class FreeBSD64Bit extends FreeBSD {
        @Override
        public String getId() {
            return "freebsd-amd64";
        }
    }

    private static abstract class MacOs extends Posix {
        @Override
        public boolean isMacOs() {
            return true;
        }

        @Override
        public String getLibraryName() {
            return "libnative-platform.dylib";
        }

        @Override
        String getCursesLibraryName() {
            return "libnative-platform-curses.dylib";
        }

        @Override
        public <T extends NativeIntegration> T get(Class<T> type, NativeLibraryLoader nativeLibraryLoader) {
            if (type.equals(OsxMemory.class)) {
                return type.cast(new DefaultOsxMemory());
            }
            if (type.equals(Memory.class)) {
                return type.cast(new DefaultMemory());
            }
            return super.get(type, nativeLibraryLoader);
        }
    }

    private static class MacOs32Bit extends MacOs {
        @Override
        public String getId() {
            return "osx-i386";
        }
    }

    private static class MacOs64Bit extends MacOs {
        @Override
        public String getId() {
            return "osx-amd64";
        }
    }

    private static class MacOsAarch64 extends MacOs {
        @Override
        public String getId() {
            return "osx-aarch64";
        }
    }

    private static class Unsupported extends Platform {
        @Override
        public String getId() {
            throw new UnsupportedOperationException();
        }
    }
}
