package gradlebuild;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry;

import static gradlebuild.JavaNativeInterfaceLibraryUtils.library;

public class FreeBsdRuntimePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(FreeBsdToolChainRules.class);

        // We depend on custom JNI plugin because we are appending a new target machine
        //   The custom JNI plugin overwrite the target machines
        project.getPluginManager().withPlugin("gradlebuild.jni-nokee", ignored -> {
            library(project, library -> {
                library.getTargetMachines().add(library.getMachines().getFreeBSD().architecture("amd64"));
            });
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    public static class FreeBsdToolChainRules extends RuleSource {
        @Mutate void configureToolChains(NativeToolChainRegistry toolChainRegistry) {
            toolChainRegistry.named("clang", Clang.class, toolChain ->
                toolChain.eachPlatform(platformToolChain -> {
                    NativePlatform platform = platformToolChain.getPlatform();
                    if (platform.getOperatingSystem().isFreeBSD()) {
                        platformToolChain.getcCompiler().setExecutable("cc");
                        platformToolChain.getCppCompiler().setExecutable("c++");
                        platformToolChain.getLinker().setExecutable("c++");
                    }
                }));
        }
    }
}
