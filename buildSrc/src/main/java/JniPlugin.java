import com.google.common.collect.ImmutableList;
import groovy.util.Node;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.model.Each;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.PreprocessingTool;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.Tool;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.platform.Architecture;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.OperatingSystem;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.Gcc;
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain;
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry;
import org.gradle.nativeplatform.toolchain.VisualCpp;
import org.gradle.platform.base.PlatformContainer;

import java.io.File;
import java.util.List;

public abstract class JniPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(NativePlatformComponentPlugin.class);
        VariantsExtension variants = project.getExtensions().getByType(VariantsExtension.class);
        TaskContainer tasks = project.getTasks();
        TaskProvider<JavaCompile> compileJavaProvider = tasks.named("compileJava", JavaCompile.class);

        tasks.withType(CppCompile.class)
            .configureEach(task -> task.includes(
                compileJavaProvider.flatMap(it -> it.getOptions().getHeaderOutputDirectory())
            ));
        project.getPluginManager().apply(JniRules.class);

        project.getExtensions().configure(
            PublishingExtension.class,
            extension -> extension.getPublications().named("main", MavenPublication.class, main -> {
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
                });
            }));
    }

    public static class JniRules extends RuleSource {
        @Mutate
        void createPlatforms(PlatformContainer platformContainer) {
            addPlatform(platformContainer, "osx_amd64", "osx", "amd64");
            addPlatform(platformContainer, "linux_amd64", "linux", "amd64");
            addPlatform(platformContainer, "linux_amd64_ncurses5", "linux", "amd64");
            addPlatform(platformContainer, "linux_amd64_ncurses6", "linux", "amd64");
            addPlatform(platformContainer, "linux_aarch64", "linux", "aarch64");
            addPlatform(platformContainer, "linux_aarch64_ncurses5", "linux", "aarch64");
            addPlatform(platformContainer, "linux_aarch64_ncurses6", "linux", "aarch64");
            addPlatform(platformContainer, "windows_i386", "windows", "i386");
            addPlatform(platformContainer, "windows_amd64", "windows", "amd64");
            addPlatform(platformContainer, "windows_i386_min", "windows", "i386");
            addPlatform(platformContainer, "windows_amd64_min", "windows", "amd64");
            addPlatform(platformContainer, "freebsd_amd64_libcpp", "freebsd", "amd64");
        }

        private void addPlatform(PlatformContainer platformContainer, String name, String os, String architecture) {
            platformContainer.create(name, NativePlatform.class, platform -> {
                platform.operatingSystem(os);
                platform.architecture(architecture);
            });
        }

        @Mutate void createToolChains(NativeToolChainRegistry toolChainRegistry) {
            toolChainRegistry.create("gcc", Gcc.class, toolChain -> {
                // The core Gradle toolchain for gcc only targets x86 and x86_64 out of the box.
                // https://github.com/gradle/gradle/blob/36614ee523e5906ddfa1fed9a5dc00a5addac1b0/subprojects/platform-native/src/main/java/org/gradle/nativeplatform/toolchain/internal/gcc/AbstractGccCompatibleToolChain.java
                toolChain.target("linux_aarch64");
                toolChain.target("linux_aarch64_ncurses5");
                toolChain.target("linux_aarch64_ncurses6");
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

                });
            });
            toolChainRegistry.create("clang", Clang.class, toolChain -> {
                // The core Gradle toolchain for gcc only targets x86 and x86_64 out of the box.
                // https://github.com/gradle/gradle/blob/36614ee523e5906ddfa1fed9a5dc00a5addac1b0/subprojects/platform-native/src/main/java/org/gradle/nativeplatform/toolchain/internal/gcc/AbstractGccCompatibleToolChain.java
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
                });
            });
            toolChainRegistry.create("visualCpp", VisualCpp.class);
        }

        private void disableToolChain(GccPlatformToolChain platformToolChain) {
            // Use a dummy so that GCC is not selected
            platformToolChain.getcCompiler().setExecutable("dummy");
            platformToolChain.getCppCompiler().setExecutable("dummy");
            platformToolChain.getObjcCompiler().setExecutable("dummy");
            platformToolChain.getObjcppCompiler().setExecutable("dummy");
            platformToolChain.getAssembler().setExecutable("dummy");
            platformToolChain.getLinker().setExecutable("dummy");
            platformToolChain.getStaticLibArchiver().setExecutable("dummy");
        }

        private void enableToolChain(GccPlatformToolChain platformToolChain) {
            platformToolChain.getcCompiler().setExecutable("cc");
            platformToolChain.getCppCompiler().setExecutable("c++");
            platformToolChain.getLinker().setExecutable("c++");
        }

        @Mutate void configureBinaries(@Each NativeBinarySpecInternal binarySpec) {
            DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
            Architecture currentArch = currentPlatform.getArchitecture();
            NativePlatform targetPlatform = binarySpec.getTargetPlatform();
            Architecture targetArch = targetPlatform.getArchitecture();
            OperatingSystem targetOs = targetPlatform.getOperatingSystem();
            if (ImmutableList.of("linux", "freebsd").contains(targetArch.getName()) && targetArch != currentArch) {
                // Native plugins don't detect whether multilib support is available or not. Assume not for now
                binarySpec.setBuildable(false);
            }

            PreprocessingTool cppCompiler = binarySpec.getCppCompiler();
            Tool linker = binarySpec.getLinker();
            if (targetOs.isMacOsX()) {
                cppCompiler.getArgs().addAll(determineJniIncludes("darwin"));
                cppCompiler.args("-mmacosx-version-min=10.9");
                linker.args("-mmacosx-version-min=10.9");
                linker.args("-framework", "CoreServices");
            } else if (targetOs.isLinux()) {
                cppCompiler.getArgs().addAll(determineJniIncludes("linux"));
                cppCompiler.args("-D_FILE_OFFSET_BITS=64");
            } else if (targetOs.isWindows()) {
                if (binarySpec.getName().contains("_min")) {
                    cppCompiler.define("WINDOWS_MIN");
                }
                cppCompiler.getArgs().addAll(determineJniIncludes("win32"));
                linker.args("Shlwapi.lib", "Advapi32.lib");
            } else if (targetOs.isFreeBSD()) {
                cppCompiler.getArgs().addAll(determineJniIncludes("freebsd"));
            }
        }

        @Mutate void configureSharedLibraryBinaries(@Each SharedLibraryBinarySpec binarySpec, ExtensionContainer extensions, ServiceRegistry serviceRegistry) {
            // Only depend on variants which can be built on the current machine
            boolean onlyLocalVariants = serviceRegistry.get(ProviderFactory.class).gradleProperty("onlyLocalVariants").forUseAtConfigurationTime().isPresent();
            if (onlyLocalVariants && !binarySpec.isBuildable()) {
                return;
            }
            String variantName = binaryToVariantName(binarySpec);
            extensions.getByType(VariantsExtension.class).getVariantNames().add(variantName);
        }

        private List<String> determineJniIncludes(String osSpecificInclude) {
            Jvm currentJvm = Jvm.current();
            File jvmIncludePath = new File(currentJvm.getJavaHome(), "include");
            return ImmutableList.of(
                "-I", jvmIncludePath.getAbsolutePath(),
                "-I", new File(jvmIncludePath, osSpecificInclude).getAbsolutePath()
            );
        }
    }

    private static String binaryToVariantName(NativeBinarySpec binary) {
        return binary.getTargetPlatform().getName().replace('_', '-');
    }
}
