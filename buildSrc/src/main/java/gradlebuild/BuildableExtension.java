package gradlebuild;

import dev.nokee.platform.jni.JniLibrary;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Provider;

import java.util.Set;

import static gradlebuild.NcursesVersion.NCURSES_5;
import static gradlebuild.NcursesVersion.NCURSES_6;

public final class BuildableExtension {
    private final Project project;
    private final JniLibrary variant;

    BuildableExtension(Project project, JniLibrary variant) {
        this.project = project;
        this.variant = variant;
    }

    public boolean isBuildable() {
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

    public static boolean isBuildable(JniLibrary variant) {
        return ((ExtensionAware) variant).getExtensions().getByType(BuildableExtension.class).isBuildable();
    }
}
