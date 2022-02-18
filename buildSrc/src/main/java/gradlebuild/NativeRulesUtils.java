package gradlebuild;

import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain;
import org.gradle.platform.base.PlatformContainer;

public interface NativeRulesUtils {
    static void addPlatform(PlatformContainer platformContainer, String name, String os, String architecture) {
        platformContainer.create(name, NativePlatform.class, platform -> {
            platform.operatingSystem(os);
            platform.architecture(architecture);
        });
    }

    static void disableToolChain(GccPlatformToolChain platformToolChain) {
        // Use a dummy so that Clang/GCC is not selected
        platformToolChain.getcCompiler().setExecutable("dummy");
        platformToolChain.getCppCompiler().setExecutable("dummy");
        platformToolChain.getObjcCompiler().setExecutable("dummy");
        platformToolChain.getObjcppCompiler().setExecutable("dummy");
        platformToolChain.getAssembler().setExecutable("dummy");
        platformToolChain.getLinker().setExecutable("dummy");
        platformToolChain.getStaticLibArchiver().setExecutable("dummy");
    }
}
