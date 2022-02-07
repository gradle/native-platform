package gradlebuild;

import dev.nokee.platform.jni.JavaNativeInterfaceLibrary;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.ExtensionAware;

final class JavaNativeInterfaceLibraryUtils {
    private JavaNativeInterfaceLibraryUtils() {}

    public static ConfigurableFileCollection cppSources(JavaNativeInterfaceLibrary library) {
        return (ConfigurableFileCollection) ((ExtensionAware) library).getExtensions().getByName("cppSources");
    }

    public static ConfigurableFileCollection privateHeaders(JavaNativeInterfaceLibrary library) {
        return (ConfigurableFileCollection) ((ExtensionAware) library).getExtensions().getByName("privateHeaders");
    }

    public static void library(Project project, Action<? super JavaNativeInterfaceLibrary> action) {
        project.getExtensions().configure("library", action);
    }
}
