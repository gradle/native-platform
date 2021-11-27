package gradlebuild;

import com.google.common.collect.ImmutableSet;
import dev.nokee.platform.jni.JavaNativeInterfaceLibrary;
import dev.nokee.runtime.nativebase.TargetMachine;
import dev.nokee.runtime.nativebase.TargetMachineFactory;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.platform.OperatingSystem;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.Gcc;
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry;
import org.gradle.nativeplatform.toolchain.VisualCpp;

import java.io.File;
import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
public abstract class JniNokeePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(NativePlatformComponentPlugin.class);
        project.getPluginManager().apply("dev.nokee.jni-library");
        project.getPluginManager().apply(NativeToolChainRules.class);

        configureMainLibrary(project.getExtensions().getByType(JavaNativeInterfaceLibrary.class));

        configureNativeVersionGeneration(project);
    }

    private void configureMainLibrary(JavaNativeInterfaceLibrary library) {
        library.getTargetMachines().set(supportedMachines(library.getMachines()));
    }

    private static Set<TargetMachine> supportedMachines(TargetMachineFactory machines) {
        ImmutableSet.Builder<TargetMachine> result = ImmutableSet.builder();
        result.add(machines.os("osx").architecture("amd64"));
        result.add(machines.os("osx").architecture("aarch64"));
        result.add(machines.getLinux().architecture("amd64"));
        result.add(machines.getLinux().architecture("aarch64"));
        result.add(machines.getWindows().architecture("i386"));
        result.add(machines.getWindows().architecture("amd64"));
        return result.build();
    }

    private void configureNativeVersionGeneration(Project project) {
        NativePlatformVersionExtension nativeVersion = project.getExtensions().create("nativeVersion", NativePlatformVersionExtension.class);

        File generatedFilesDir = new File(project.getBuildDir(), "generated");

        TaskProvider<WriteNativeVersionSources> writeNativeVersionSources = project.getTasks().register("writeNativeVersionSources", WriteNativeVersionSources.class, task -> {
            task.getGeneratedNativeHeaderDirectory().set(new File(generatedFilesDir, "version/header"));
            task.getGeneratedJavaSourcesDir().set(new File(generatedFilesDir, "version/java"));
            task.getVersionClassPackageName().set(nativeVersion.getVersionClassPackageName());
            task.getVersionClassName().set(nativeVersion.getVersionClassName());
        });


        project.getTasks().withType(CppCompile.class).configureEach(task ->
            task.includes(writeNativeVersionSources.flatMap(WriteNativeVersionSources::getGeneratedNativeHeaderDirectory)
        ));
        JavaPluginConvention javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        javaPluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).java(javaSources ->
            javaSources.srcDir(writeNativeVersionSources.flatMap(WriteNativeVersionSources::getGeneratedJavaSourcesDir))
        );
    }

    public static class NativeToolChainRules extends RuleSource {
        @Mutate
        void createToolChains(NativeToolChainRegistry toolChainRegistry) {
            toolChainRegistry.create("gcc", Gcc.class, toolChain -> {
                // The core Gradle toolchain for gcc only targets x86 and x86_64 out of the box.
                // https://github.com/gradle/gradle/blob/36614ee523e5906ddfa1fed9a5dc00a5addac1b0/subprojects/platform-native/src/main/java/org/gradle/nativeplatform/toolchain/internal/gcc/AbstractGccCompatibleToolChain.java
                toolChain.target("linuxaarch64");
            });
            toolChainRegistry.create("clang", Clang.class, toolChain -> {
                // The core Gradle toolchain for Clang only targets x86 and x86_64 out of the box.
                OperatingSystem os = new DefaultNativePlatform("current").getOperatingSystem();
                if (os.isMacOsX()) {
                    toolChain.target("macosaarch64");
                }
            });
            toolChainRegistry.create("visualCpp", VisualCpp.class);
        }
    }
}
