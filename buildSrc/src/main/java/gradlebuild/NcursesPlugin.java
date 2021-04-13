package gradlebuild;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.model.Each;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.OperatingSystem;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.toolchain.Gcc;
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry;
import org.gradle.platform.base.PlatformContainer;

import java.io.File;
import java.util.Collection;
import java.util.Objects;

import static gradlebuild.NativeRulesUtils.addPlatform;

public class NcursesPlugin extends RuleSource {

    private static final NcursesVersion NCURSES_5 = new NcursesVersion("5");

    @Model Collection<NcursesVersion> ncursesVersions() {
        OperatingSystem os = new DefaultNativePlatform("current").getOperatingSystem();
        ImmutableSet.Builder<NcursesVersion> builder = ImmutableSet.builder();
        if (!os.isLinux()) {
            builder.add(NCURSES_5);
        } else {
            for (String d : ImmutableList.of("/lib", "/lib64", "/lib/x86_64-linux-gnu", "/lib/aarch64-linux-gnu", "/usr/lib")) {
                File libDir = new File(d);
                if (new File(libDir, "libncurses.so.6").isFile() || new File(libDir, "libncursesw.so.6").isFile()) {
                    builder.add(new NcursesVersion("6"));
                }
                if (new File(libDir, "libncurses.so.5").isFile()) {
                    builder.add(new NcursesVersion("5"));
                }
            }
        }
        ImmutableSet<NcursesVersion> versions = builder.build();
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("Could not determine ncurses version installed on this machine.");
        }
        return versions;
    }

    @Mutate void createPlatforms(PlatformContainer platformContainer) {
        addPlatform(platformContainer, "linux_amd64_ncurses5", "linux", "amd64");
        addPlatform(platformContainer, "linux_amd64_ncurses6", "linux", "amd64");
        addPlatform(platformContainer, "linux_aarch64_ncurses5", "linux", "aarch64");
        addPlatform(platformContainer, "linux_aarch64_ncurses6", "linux", "aarch64");
    }

    @Mutate void configureBinaries(@Each NativeBinarySpecInternal binarySpec, Collection<NcursesVersion> ncursesVersions) {
        NativePlatform targetPlatform = binarySpec.getTargetPlatform();
        if (targetPlatform.getOperatingSystem().isLinux() && targetPlatform.getName().contains("ncurses")) {
            if (!ncursesVersions.stream().anyMatch(ncursesVersion -> isNcursesVersion(targetPlatform, ncursesVersion))) {
                binarySpec.setBuildable(false);
            }
        }
        if (binarySpec.getComponent().getName().contains("Curses")) {
            if (targetPlatform.getOperatingSystem().isLinux() && !isNcursesVersion(targetPlatform, NCURSES_5)) {
                binarySpec.getLinker().args("-lncursesw");
            } else {
                binarySpec.getLinker().args("-lcurses");
            }
        }
    }

    private boolean isNcursesVersion(NativePlatform targetPlatform, NcursesVersion ncursesVersion) {
        return targetPlatform.getName().contains("ncurses" + ncursesVersion.getVersionNumber());
    }

    @Mutate void configureToolChains(NativeToolChainRegistry toolChainRegistry) {
        toolChainRegistry.named("gcc", Gcc.class, toolChain -> {
            // The core Gradle toolchain for gcc only targets x86 and x86_64 out of the box.
            // https://github.com/gradle/gradle/blob/36614ee523e5906ddfa1fed9a5dc00a5addac1b0/subprojects/platform-native/src/main/java/org/gradle/nativeplatform/toolchain/internal/gcc/AbstractGccCompatibleToolChain.java
            toolChain.target("linux_aarch64_ncurses5");
            toolChain.target("linux_aarch64_ncurses6");
        });
    }

    public static class NcursesVersion {
        private final String versionNumber;

        public NcursesVersion(String versionNumber) {
            this.versionNumber = versionNumber;
        }

        public String getVersionNumber() {
            return versionNumber;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NcursesVersion that = (NcursesVersion) o;
            return versionNumber.equals(that.versionNumber);
        }

        @Override
        public int hashCode() {
            return Objects.hash(versionNumber);
        }
    }
}
