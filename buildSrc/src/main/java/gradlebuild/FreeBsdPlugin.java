package gradlebuild;

import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.Gcc;
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain;
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry;
import org.gradle.platform.base.PlatformContainer;

import static gradlebuild.NativeRulesUtils.addPlatform;

public class FreeBsdPlugin extends RuleSource {
    @Mutate void createPlatforms(PlatformContainer platformContainer) {
        addPlatform(platformContainer, "freebsd_amd64_libcpp", "freebsd", "amd64");
    }

    @Mutate void configureToolChains(NativeToolChainRegistry toolChainRegistry) {
        toolChainRegistry.named("gcc", Gcc.class, toolChain ->
            toolChain.eachPlatform(platformToolChain -> {
                // Use GCC to build for libstdc++ on FreeBSD
                NativePlatform platform = platformToolChain.getPlatform();
                if (platform.getOperatingSystem().isFreeBSD()) {
                    if (platform.getName().contains("libstdcpp")) {
                        enableToolChain(platformToolChain);
                    } else {
                        disableToolChain(platformToolChain);
                    }
                }
            }));
        toolChainRegistry.named("clang", Clang.class, toolChain ->
            toolChain.eachPlatform(platformToolChain -> {
                // Use GCC to build for libstdc++ on FreeBSD
                NativePlatform platform = platformToolChain.getPlatform();
                if (platform.getOperatingSystem().isFreeBSD()) {
                    if (platform.getName().contains("libcpp")) {
                        enableToolChain(platformToolChain);
                    } else {
                        // Use a dummy so that GCC is not selected
                        disableToolChain(platformToolChain);
                    }
                }
            }));
    }

    private static void enableToolChain(GccPlatformToolChain platformToolChain) {
        platformToolChain.getcCompiler().setExecutable("cc");
        platformToolChain.getCppCompiler().setExecutable("c++");
        platformToolChain.getLinker().setExecutable("c++");
    }

    private static void disableToolChain(GccPlatformToolChain platformToolChain) {
        // Use a dummy so that GCC is not selected
        platformToolChain.getcCompiler().setExecutable("dummy");
        platformToolChain.getCppCompiler().setExecutable("dummy");
        platformToolChain.getObjcCompiler().setExecutable("dummy");
        platformToolChain.getObjcppCompiler().setExecutable("dummy");
        platformToolChain.getAssembler().setExecutable("dummy");
        platformToolChain.getLinker().setExecutable("dummy");
        platformToolChain.getStaticLibArchiver().setExecutable("dummy");
    }
}
