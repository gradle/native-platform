import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.cpp.tasks.CppCompile;

public abstract class JniPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", plugin -> {
            TaskContainer tasks = project.getTasks();
            TaskProvider<JavaCompile> compileJavaProvider = tasks.named("compileJava", JavaCompile.class);
            configureCompileJava(compileJavaProvider);

            tasks.withType(CppCompile.class)
                .configureEach(task -> task.includes(
                    compileJavaProvider.flatMap(it -> it.getOptions().getHeaderOutputDirectory())
                ));
        });
    }

    private void configureCompileJava(
        TaskProvider<JavaCompile> compileJavaProvider
    ) {
        compileJavaProvider.configure(compileJava -> {
            DirectoryProperty headerOutputDirectory = compileJava.getOptions().getHeaderOutputDirectory();
            headerOutputDirectory.convention(compileJava.getProject().getLayout().getBuildDirectory().dir("generated/jni-headers"));
            // The nested output is not marked automatically as an output of the task regarding task dependencies.
            // So we mark it manually here.
            // See https://github.com/gradle/gradle/issues/6619.
            compileJava.getOutputs().dir(compileJava.getOptions().getHeaderOutputDirectory());
            // Cannot do incremental header generation, since the pattern for cleaning them up is currently wrong.
            // See https://github.com/gradle/gradle/issues/12084.
            compileJava.getOptions().setIncremental(false);
        });
    }
}
