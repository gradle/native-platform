package gradlebuild;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.nokee.platform.base.BuildVariant;
import dev.nokee.platform.base.Variant;
import dev.nokee.platform.jni.JavaNativeInterfaceLibrary;
import dev.nokee.runtime.nativebase.OperatingSystemFamily;
import dev.nokee.runtime.nativebase.TargetMachineFactory;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.specs.Spec;
import org.gradle.nativeplatform.platform.OperatingSystem;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;

import java.io.File;
import java.util.Set;
import java.util.concurrent.Callable;

import static gradlebuild.JavaNativeInterfaceLibraryUtils.library;
import static gradlebuild.NcursesVersion.NCURSES_5;
import static gradlebuild.NcursesVersion.NCURSES_6;
import static java.util.Arrays.stream;

@SuppressWarnings("UnstableApiUsage")
public class NcursesRuntimePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getExtensions().add(new TypeOf<Provider<Set<NcursesVersion>>>() {}, "availableNcursesVersions", availableNcursesVersions(project));

        project.getPluginManager().withPlugin("dev.nokee.jni-library", appliedPlugin -> {
            library(project, library -> {
                // Register ncurses dimension
                SetProperty<NcursesVersion> ncurses = registerNcursesDimension(library);

                // Defaults to all ncurses versions
                ncurses.convention(ImmutableSet.copyOf(NcursesVersion.values()));

                // Register dimension as library extensions
                ((ExtensionAware) library).getExtensions().add("targetNcurses", ncurses);

                library.getVariants().configureEach(ncursesVariant(), variant -> {
                    variant.getSharedLibrary().getLinkTask().configure(task -> {
                        task.getLinkerArgs().addAll(project.provider(ofNcursesLibraryFlags(library.getMachines(), variant.getBuildVariant())));
                    });
                });
            });
        });
    }

    private static Spec<Variant> ncursesVariant() {
        return variant -> stream(NcursesVersion.values())
            .anyMatch(it -> variant.getBuildVariant().hasAxisOf(it));
    }

    private static Callable<Iterable<String>> ofNcursesLibraryFlags(TargetMachineFactory machines, BuildVariant buildVariant) {
        return () -> {
            if (buildVariant.hasAxisOf(machines.getLinux().getOperatingSystemFamily()) && !buildVariant.hasAxisOf(NCURSES_5)) {
                return ImmutableList.of("-lncursesw");
            } else {
                return ImmutableList.of("-lcurses");
            }
        };
    }

    private SetProperty<NcursesVersion> registerNcursesDimension(JavaNativeInterfaceLibrary library) {
        return library.getDimensions().newAxis(NcursesVersion.class, it -> it.onlyIf(OperatingSystemFamily.class, (ncurses, osFamily) -> {
            return !ncurses.isPresent() || osFamily.isLinux()
                || (!osFamily.isWindows() && ncurses.get().equals(NCURSES_5));
        }));
    }

    private Provider<Set<NcursesVersion>> availableNcursesVersions(Project project) {
        final SetProperty<NcursesVersion> availableNcursesVersions = project.getObjects().setProperty(NcursesVersion.class);
        availableNcursesVersions.value(project.provider(() -> {
            final OperatingSystem os = new DefaultNativePlatform("current").getOperatingSystem();
            final ImmutableSet.Builder<NcursesVersion> builder = ImmutableSet.builder();
            if (!os.isLinux()) {
                builder.add(NCURSES_5);
            } else {
                for (String d : ImmutableList.of("/lib", "/lib64", "/lib/x86_64-linux-gnu", "/lib/aarch64-linux-gnu", "/usr/lib")) {
                    File libDir = new File(d);
                    if (new File(libDir, "libncurses.so.6").isFile() || new File(libDir, "libncursesw.so.6").isFile()) {
                        builder.add(NCURSES_6);
                    }
                    if (new File(libDir, "libncurses.so.5").isFile()) {
                        builder.add(NCURSES_5);
                    }
                }
            }
            final ImmutableSet<NcursesVersion> versions = builder.build();
            if (versions.isEmpty()) {
                throw new IllegalArgumentException("Could not determine ncurses version installed on this machine.");
            }
            return versions;
        }));
        availableNcursesVersions.finalizeValueOnRead();
        availableNcursesVersions.disallowChanges();
        return availableNcursesVersions;
    }
}
