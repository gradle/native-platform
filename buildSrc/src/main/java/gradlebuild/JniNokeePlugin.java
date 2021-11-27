package gradlebuild;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.cpp.tasks.CppCompile;

import java.io.File;

@SuppressWarnings("UnstableApiUsage")
public abstract class JniNokeePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(NativePlatformComponentPlugin.class);
        project.getPluginManager().apply("dev.nokee.jni-library");

        configureNativeVersionGeneration(project);
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
}
