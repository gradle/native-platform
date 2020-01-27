import com.google.common.collect.ImmutableList;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.process.CommandLineArgumentProvider;

public abstract class JniPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", plugin -> {
            JniExtension jniExtension = project.getExtensions().create("jni", JniExtension.class);
            JniCompilerArguments compilerArguments = new JniCompilerArguments(project.getLayout().getBuildDirectory().dir("generated/jni-headers"));
            TaskContainer tasks = project.getTasks();
            TaskProvider<JavaCompile> compileJavaProvider = tasks.named("compileJava", JavaCompile.class);
            configureCompileJava(compilerArguments, compileJavaProvider);

            TaskProvider<ConcatenateJniHeaders> concatenateJniHeaders = createConcatenateJniHeadersTask(
                tasks,
                compileJavaProvider,
                compilerArguments.getGeneratedHeadersDirectory(),
                jniExtension.getGeneratedHeaderDirectory()
            );

            configureIncludePath(tasks, concatenateJniHeaders.map(ConcatenateJniHeaders::getGeneratedNativeHeaderDirectory));
        });
    }

    private TaskProvider<ConcatenateJniHeaders> createConcatenateJniHeadersTask(TaskContainer tasks, TaskProvider<JavaCompile> compileJavaProvider, Provider<Directory> sourceHeaderDirectory, DirectoryProperty targetHeaderDirectory) {
        return tasks.register("concatenateJniHeaders", ConcatenateJniHeaders.class, task -> {
                    task.getJniHeaders().set(sourceHeaderDirectory);
                    task.getGeneratedNativeHeaderDirectory().set(targetHeaderDirectory);
                    task.dependsOn(compileJavaProvider);
                });
    }

    private void configureCompileJava(JniCompilerArguments compilerArguments, TaskProvider<JavaCompile> compileJavaProvider) {
        compileJavaProvider.configure(compileJava -> {
            compileJava.getOptions().getCompilerArgumentProviders().add(compilerArguments);
            // Cannot do incremental header generation
            compileJava.getOptions().setIncremental(false);
        });
    }

    private void configureIncludePath(TaskContainer tasks, Provider<DirectoryProperty> generatedHeaderDirectory) {
        tasks.withType(CppCompile.class).configureEach(task -> {
            task.includes(generatedHeaderDirectory);
            task.dependsOn(generatedHeaderDirectory);
        });
    }

    private static class JniCompilerArguments implements CommandLineArgumentProvider {
        private final Provider<Directory> generatedHeadersDirectory;

        public JniCompilerArguments(Provider<Directory> generatedHeadersDirectory) {
            this.generatedHeadersDirectory = generatedHeadersDirectory;
        }

        @OutputDirectory
        public Provider<Directory> getGeneratedHeadersDirectory() {
            return generatedHeadersDirectory;
        }

        @Override
        public Iterable<String> asArguments() {
            return ImmutableList.of("-h", generatedHeadersDirectory.get().getAsFile().getAbsolutePath());
        }
    }
}
