package gradlebuild;

import com.google.common.collect.ImmutableList;
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

import static gradlebuild.NativeRulesUtils.addPlatform;

public class NcursesPlugin extends RuleSource {

    @Model NcursesVersion ncursesVersion() {
        return new NcursesVersion(inferNCursesVersion());
    }

    @Mutate void createPlatforms(PlatformContainer platformContainer) {
        addPlatform(platformContainer, "linux_amd64_ncurses5", "linux", "amd64");
        addPlatform(platformContainer, "linux_amd64_ncurses6", "linux", "amd64");
        addPlatform(platformContainer, "linux_aarch64_ncurses5", "linux", "aarch64");
        addPlatform(platformContainer, "linux_aarch64_ncurses6", "linux", "aarch64");
    }

    @Mutate void configureBinaries(@Each NativeBinarySpecInternal binarySpec, NcursesVersion ncursesVersion) {
        NativePlatform targetPlatform = binarySpec.getTargetPlatform();
        if (targetPlatform.getOperatingSystem().isLinux() && targetPlatform.getName().contains("ncurses")) {
            if (!targetPlatform.getName().contains("ncurses" + ncursesVersion.getNcursesVersion())) {
                binarySpec.setBuildable(false);
            }
        }
        if (binarySpec.getComponent().getName().contains("Curses")) {
            if (targetPlatform.getOperatingSystem().isLinux() && !ncursesVersion.getNcursesVersion().equals("5")) {
                binarySpec.getLinker().args("-lncursesw");
            } else {
                binarySpec.getLinker().args("-lcurses");
            }
        }
    }
    @Mutate void configureToolChains(NativeToolChainRegistry toolChainRegistry) {
        toolChainRegistry.named("gcc", Gcc.class, toolChain -> {
            // The core Gradle toolchain for gcc only targets x86 and x86_64 out of the box.
            // https://github.com/gradle/gradle/blob/36614ee523e5906ddfa1fed9a5dc00a5addac1b0/subprojects/platform-native/src/main/java/org/gradle/nativeplatform/toolchain/internal/gcc/AbstractGccCompatibleToolChain.java
            toolChain.target("linux_aarch64_ncurses5");
            toolChain.target("linux_aarch64_ncurses6");
        });
    }

    private static String inferNCursesVersion() {
        OperatingSystem os = new DefaultNativePlatform("current").getOperatingSystem();
        if (!os.isLinux()) {
            return "5";
        }
        for (String d : ImmutableList.of("/lib", "/lib64", "/lib/x86_64-linux-gnu", "/lib/aarch64-linux-gnu")) {
            File libDir = new File(d);
            if (new File(libDir, "libncurses.so.6").isFile()) {
                return "6";
            }
            if (new File(libDir, "libncurses.so.5").isFile()) {
                return "5";
            }
        }
        throw new IllegalArgumentException("Could not determine ncurses version installed on this machine.");
    }

    public static class NcursesVersion {
        private final String ncursesVersion;

        public NcursesVersion(String ncursesVersion) {
            this.ncursesVersion = ncursesVersion;
        }

        public String getNcursesVersion() {
            return ncursesVersion;
        }
    }
}
