package gradlebuild;

import dev.nokee.platform.jni.JavaNativeInterfaceLibrary;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.ExtensionAware;

public final class JavaNativeInterfaceLibraryProperties {
    private JavaNativeInterfaceLibraryProperties() {}

    public static ConfigurableFileCollection cppSources(JavaNativeInterfaceLibrary library) {
        return (ConfigurableFileCollection) ((ExtensionAware) library).getExtensions().getByName("cppSources");
    }

    public static ConfigurableFileCollection privateHeaders(JavaNativeInterfaceLibrary library) {
        return (ConfigurableFileCollection) ((ExtensionAware) library).getExtensions().getByName("privateHeaders");
    }
}
