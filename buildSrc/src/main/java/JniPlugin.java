import org.gradle.api.Plugin;
import org.gradle.api.Project;
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

            tasks.withType(CppCompile.class)
                .configureEach(task -> task.includes(
                    compileJavaProvider.flatMap(it -> it.getOptions().getHeaderOutputDirectory())
                ));
        });
    }
}
