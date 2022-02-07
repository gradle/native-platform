package gradlebuild;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.nokee.platform.jni.JavaNativeInterfaceLibrary;
import dev.nokee.runtime.nativebase.TargetMachine;
import dev.nokee.runtime.nativebase.TargetMachineFactory;
import gradlebuild.actions.MixInJavaNativeInterfaceLibraryProperties;
import gradlebuild.actions.RegisterJniTestTask;
import groovy.util.Node;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.platform.OperatingSystem;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.Gcc;
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry;
import org.gradle.nativeplatform.toolchain.VisualCpp;

import java.io.File;
import java.util.Set;

import static gradlebuild.JavaNativeInterfaceLibraryProperties.cppSources;
import static gradlebuild.JavaNativeInterfaceLibraryProperties.privateHeaders;

@SuppressWarnings("UnstableApiUsage")
public abstract class JniNokeePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(NativePlatformComponentPlugin.class);
        VariantsExtension variants = project.getExtensions().getByType(VariantsExtension.class);
        project.getPluginManager().apply("dev.nokee.jni-library");
        project.getPluginManager().apply(NativeToolChainRules.class);

        configureCppTasks(project);
        configureMainLibrary(project);
        configureVariants(project);
        addComponentSourcesSetsToProjectSourceSet(project.getTasks(), project.getExtensions().getByType(JavaNativeInterfaceLibrary.class));

        configureNativeVersionGeneration(project);
        configureJniTest(project);

        configurePomOfMainJar(project, variants);
    }

    private void configureJniTest(Project project) {
        new RegisterJniTestTask().execute(project);
    }

    private void configureVariants(Project project) {
        JavaNativeInterfaceLibrary library = project.getExtensions().getByType(JavaNativeInterfaceLibrary.class);
        VariantsExtension variants = project.getExtensions().getByType(VariantsExtension.class);
        variants.getVariantNames().set(library.getVariants().flatMap(it -> {
            // Only depend on variants which can be built on the current machine
            boolean onlyLocalVariants = project.getProviders().gradleProperty("onlyLocalVariants").forUseAtConfigurationTime().isPresent();
            if (onlyLocalVariants && !it.getSharedLibrary().isBuildable()) {
                return ImmutableList.of();
            } else {
                return ImmutableList.of(toVariantName(it.getTargetMachine()));
            }
        }));
    }

    private static String toVariantName(TargetMachine targetMachine) {
        return targetMachine.getOperatingSystemFamily().getName() + "-" + targetMachine.getArchitecture().getName();
    }

    private void configureMainLibrary(Project project) {
        JavaNativeInterfaceLibrary library = project.getExtensions().getByType(JavaNativeInterfaceLibrary.class);
        registerWindowsDistributionDimension(library);
        library.getTargetMachines().set(supportedMachines(library.getMachines()));
        library.getTasks().configureEach(CppCompile.class, task -> {
            task.getCompilerArgs().addAll(task.getTargetPlatform().map(targetPlatform -> {
                OperatingSystem targetOs = targetPlatform.getOperatingSystem();
                if (targetOs.isMacOsX()) {
                    return ImmutableList.of("-mmacosx-version-min=10.9");
                } else if (targetOs.isLinux()) {
                    return ImmutableList.of("-D_FILE_OFFSET_BITS=64");
                } else {
                    return ImmutableList.of(); // do nothing
                }
            }));
        });
        library.getTasks().configureEach(LinkSharedLibrary.class, task -> {
            task.getLinkerArgs().addAll(task.getTargetPlatform().map(targetPlatform -> {
                OperatingSystem targetOs = targetPlatform.getOperatingSystem();
                if (targetOs.isMacOsX()) {
                    return ImmutableList.of(
                        "-mmacosx-version-min=10.9",
                        "-framework", "CoreServices");
                } else if (targetOs.isWindows()) {
                    return ImmutableList.of("Shlwapi.lib", "Advapi32.lib");
                } else {
                    return ImmutableList.of(); // do nothing
                }
            }));
        });
        library.getVariants().configureEach(variant -> {
            if (variant.getBuildVariant().hasAxisOf(WindowsDistribution.WINDOWS_XP_OR_LOWER)) {
                variant.getTasks().configureEach(CppCompile.class, task -> {
                    task.getCompilerArgs().add("/DWINDOWS_MIN");
                });
            }
        });
        library.getVariants().configureEach(variant -> {
            variant.getResourcePath().set(String.join("/",
                project.getGroup().toString().replace('.', '/'),
                "platform",
                toVariantName(variant.getTargetMachine())));
        });
    }

    private void registerWindowsDistributionDimension(JavaNativeInterfaceLibrary library) {
        SetProperty<WindowsDistribution> newDimension = library.getDimensions().newAxis(WindowsDistribution.class, builder -> builder.onlyOn(library.getMachines().getWindows().getOperatingSystemFamily()));
        newDimension.convention(ImmutableSet.copyOf(WindowsDistribution.values()));
        ((ExtensionAware) library).getExtensions().add("targetWindowsDistributions", newDimension);
    }

    private void addComponentSourcesSetsToProjectSourceSet(TaskContainer tasks, JavaNativeInterfaceLibrary library) {
        tasks.withType(WriteNativeVersionSources.class, task -> {
            task.getNativeSources().from(cppSources(library), privateHeaders(library));
        });
    }

    private void configureCppTasks(Project project) {
        project.getPluginManager().withPlugin("dev.nokee.cpp-language", new MixInJavaNativeInterfaceLibraryProperties(project));
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

    private void configurePomOfMainJar(Project project, VariantsExtension variants) {
        project.getExtensions().configure(
            PublishingExtension.class,
            extension -> extension.getPublications().named("main", MavenPublication.class, main ->
                main.getPom().withXml(xmlProvider -> {
                    Node node = xmlProvider.asNode();
                    Node deps = node.appendNode("dependencies");
                    variants.getVariantNames().get().forEach(variantName -> {
                        Node dep = deps.appendNode("dependency");
                        dep.appendNode("groupId", project.getGroup());
                        dep.appendNode("artifactId", main.getArtifactId() + "-" + variantName);
                        dep.appendNode("version", project.getVersion());
                        dep.appendNode("scope", "runtime");
                    });
                })));
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
