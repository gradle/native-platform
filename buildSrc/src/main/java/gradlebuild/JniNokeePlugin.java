package gradlebuild;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.nokee.platform.base.BuildVariant;
import dev.nokee.platform.jni.JavaNativeInterfaceLibrary;
import dev.nokee.platform.jni.JniLibrary;
import dev.nokee.runtime.nativebase.TargetMachine;
import dev.nokee.runtime.nativebase.TargetMachineFactory;
import gradlebuild.actions.MixInJavaNativeInterfaceLibraryProperties;
import gradlebuild.actions.RegisterJniTestTask;
import groovy.util.Node;
import org.gradle.api.Namer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.SourceSet;
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

import static gradlebuild.JavaNativeInterfaceLibraryUtils.library;
import static gradlebuild.NcursesVersion.NCURSES_5;
import static gradlebuild.NcursesVersion.NCURSES_6;
import static gradlebuild.WindowsDistribution.WINDOWS_XP_OR_LOWER;

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

        configureNativeVersionGeneration(project);
        configureJniTest(project);

        configurePomOfMainJar(project, variants);
    }

    private void configureJniTest(Project project) {
        new RegisterJniTestTask().execute(project);
    }

    private void configureVariants(Project project) {
        library(project, library -> {
            VariantsExtension variants = project.getExtensions().getByType(VariantsExtension.class);
            variants.getVariantNames().set(library.getVariants().flatMap(new Transformer<Iterable<String>, JniLibrary>() {
                @Override
                public Iterable<String> transform(JniLibrary it) {
                    // Only depend on variants which can be built on the current machine
                    boolean onlyLocalVariants = project.getProviders().gradleProperty("onlyLocalVariants").forUseAtConfigurationTime().isPresent();
                    if (onlyLocalVariants && !isBuildable(it)) {
                        return ImmutableList.of();
                    } else {
                        return ImmutableList.of(JniNokeePlugin.VariantNamer.INSTANCE.determineName(it));
                    }
                }

                private boolean isBuildable(JniLibrary variant) {
                    if (!variant.getSharedLibrary().isBuildable()) {
                        return false; // strait-up not buildable
                    } else {
                        @SuppressWarnings("unchecked")
                        final Provider<Set<NcursesVersion>> availableNcursesVersions = (Provider<Set<NcursesVersion>>) project.getExtensions().findByName("availableNcursesVersions");
                        if (availableNcursesVersions == null) {
                            return true; // no known ncurses versions, assuming buildable
                        } else {
                            // For each variant with the ncurses dimension, check if the version is available
                            if (variant.getBuildVariant().hasAxisOf(NCURSES_5)) {
                                return availableNcursesVersions.get().contains(NCURSES_5);
                            } else if (variant.getBuildVariant().hasAxisOf(NCURSES_6)) {
                                return availableNcursesVersions.get().contains(NCURSES_6);
                            }
                            return true;
                        }
                    }
                }
            }));
        });
    }

    private void configureMainLibrary(Project project) {
        library(project, library -> {
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
                if (variant.getBuildVariant().hasAxisOf(WINDOWS_XP_OR_LOWER)) {
                    variant.getTasks().configureEach(CppCompile.class, task -> {
                        task.getCompilerArgs().add("/DWINDOWS_MIN");
                    });
                }
            });
            library.getVariants().configureEach(variant -> {
                variant.getResourcePath().set(String.join("/",
                    project.getGroup().toString().replace('.', '/'),
                    "platform",
                    VariantNamer.INSTANCE.determineName(variant)));
                variant.getJavaNativeInterfaceJar().getJarTask().configure(task -> {
                    task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("libs/main"));
                });
            });
        });
    }

    private void registerWindowsDistributionDimension(JavaNativeInterfaceLibrary library) {
        SetProperty<WindowsDistribution> newDimension = library.getDimensions().newAxis(WindowsDistribution.class, builder -> builder.onlyOn(library.getMachines().getWindows().getOperatingSystemFamily()));
        newDimension.convention(ImmutableSet.copyOf(WindowsDistribution.values()));
        ((ExtensionAware) library).getExtensions().add("targetWindowsDistributions", newDimension);
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
            task.getNativeSources().from(
                library(project).map(JavaNativeInterfaceLibraryUtils::cppSources),
                library(project).map(JavaNativeInterfaceLibraryUtils::privateHeaders)
            );
        });


        project.getTasks().withType(CppCompile.class).configureEach(task ->
            task.includes(writeNativeVersionSources.flatMap(WriteNativeVersionSources::getGeneratedNativeHeaderDirectory)
        ));
        JavaPluginConvention javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        javaPluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).java(javaSources ->
            javaSources.srcDir(writeNativeVersionSources.flatMap(WriteNativeVersionSources::getGeneratedJavaSourcesDir))
        );
    }

    /**
     * Determines the {@literal variantName} of a JNI variant as expected by {@literal native-platform}.
     * The project use the {@literal variantName} for JNI resource path, publishable JARs and buildable check.
     */
    private static final class VariantNamer implements Namer<JniLibrary> {
        public static final VariantNamer INSTANCE = new VariantNamer();

        @Override
        public String determineName(JniLibrary variant) {
            if (variant.getTargetMachine().getOperatingSystemFamily().isFreeBSD()) {
                return toVariantName(variant.getTargetMachine()) + cppRuntimeSuffix(variant.getBuildVariant());
            } else if (variant.getTargetMachine().getOperatingSystemFamily().isWindows()) {
                return toVariantName(variant.getTargetMachine()) + minSuffix(variant.getBuildVariant());
            } else if (variant.getTargetMachine().getOperatingSystemFamily().isLinux()) {
                return toVariantName(variant.getTargetMachine()) + ncursesSuffix(variant.getBuildVariant());
            } else {
                // No suffix, just use OS family and architecture names
                return toVariantName(variant.getTargetMachine());
            }
        }

        private static String cppRuntimeSuffix(BuildVariant buildVariant) {
            // The project only support libcpp.
            //   To match the artifact id for backward compatibility, we use libcpp.
            return "-libcpp"; // for backward compatibility
        }

        private static String minSuffix(BuildVariant buildVariant) {
            return buildVariant.hasAxisOf(WINDOWS_XP_OR_LOWER) ? "-min" : "";
        }

        private static String ncursesSuffix(BuildVariant buildVariant) {
            if (buildVariant.hasAxisOf(NCURSES_5)) {
                return "-ncurses5";
            } else if (buildVariant.hasAxisOf(NCURSES_6)) {
                return "-ncurses6";
            } else {
                return "";
            }
        }

        private static String toVariantName(TargetMachine targetMachine) {
            return targetMachine.getOperatingSystemFamily().getName() + "-" + targetMachine.getArchitecture().getName();
        }
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
