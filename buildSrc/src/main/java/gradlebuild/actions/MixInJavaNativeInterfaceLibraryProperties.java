package gradlebuild.actions;

import dev.nokee.language.cpp.CppSourceSet;
import dev.nokee.language.nativebase.internal.HasConfigurableHeaders;
import dev.nokee.platform.jni.JavaNativeInterfaceLibrary;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * This action bring the future to the present.
 * In future release of Nokee, language plugins will mix-in their own {@literal ConfigurableFileCollection} properties on the component.
 * For cpp-language, the properties {@literal cppSources} and {@literal privateHeaders} will be mixed in representing the component's sources as well as the conventional source layout.
 * The action is an accurate approximation of what will be done automatically by Nokee's plugins.
 */
public final class MixInJavaNativeInterfaceLibraryProperties implements Action<AppliedPlugin> {
    private final Project project;

    public MixInJavaNativeInterfaceLibraryProperties(Project project) {
        this.project = project;
    }

    @Override
    public void execute(AppliedPlugin ignored) {
        JavaNativeInterfaceLibrary library = project.getExtensions().getByType(JavaNativeInterfaceLibrary.class);
        final ConfigurableFileCollection cppSources = project.getObjects().fileCollection().from("src/main/cpp");
        final ConfigurableFileCollection privateHeaders = project.getObjects().fileCollection().from("src/main/headers");

        ((ExtensionAware) library).getExtensions().add(ConfigurableFileCollection.class, "cppSources", cppSources);
        ((ExtensionAware) library).getExtensions().add(ConfigurableFileCollection.class, "privateHeaders", privateHeaders);

        library.getSources().configureEach(CppSourceSet.class, sourceSet -> {
            sourceSet.from(cppSources);
            ((HasConfigurableHeaders) sourceSet).getHeaders().from(privateHeaders);

            // We rewire the generated JNI headers here
            //   because the convention gets overwritten on the first {@literal from} call.
            //   The convention idea is something that will be phased out in the near future.
            ((HasConfigurableHeaders) sourceSet).getHeaders().from(project.getTasks().named("compileJava", JavaCompile.class).flatMap(it -> it.getOptions().getHeaderOutputDirectory()));
        });
    }
}
