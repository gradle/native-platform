import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.process.CommandLineArgumentProvider;

import javax.inject.Inject;

public abstract class JniPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", plugin -> {
            JniExtension jniExtension = project.getExtensions().create("jni", JniExtension.class);
            jniExtension.getGeneratedHeadersDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/jni-headers"));
            JniCompilerArguments compilerArguments = new JniCompilerArguments(jniExtension.getGeneratedHeadersDirectory());
            TaskContainer tasks = project.getTasks();
            TaskProvider<JavaCompile> compileJavaProvider = tasks.named("compileJava", JavaCompile.class);
            RemoveGeneratedNativeHeaders removeGeneratedNativeHeaders = project.getObjects().newInstance(RemoveGeneratedNativeHeaders.class, compilerArguments.getGeneratedHeadersDirectory());
            configureCompileJava(compilerArguments, removeGeneratedNativeHeaders, compileJavaProvider);
            configureIncludePath(
                tasks,
                compileJavaProvider.flatMap(it -> compilerArguments.getGeneratedHeadersDirectory())
            );
        });
    }

    private void configureCompileJava(
        JniCompilerArguments compilerArguments,
        RemoveGeneratedNativeHeaders removeGeneratedNativeHeaders,
        TaskProvider<JavaCompile> compileJavaProvider
    ) {
        compileJavaProvider.configure(compileJava -> {
            compileJava.getOptions().getCompilerArgumentProviders().add(compilerArguments);
            // Cannot do incremental header generation
            compileJava.getOptions().setIncremental(false);
            compileJava.doFirst(removeGeneratedNativeHeaders);
        });
    }

    private void configureIncludePath(TaskContainer tasks, Provider<Directory> generatedHeaderDirectory) {
        tasks.withType(CppCompile.class).configureEach(task -> {
            task.includes(generatedHeaderDirectory);
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

    abstract static class RemoveGeneratedNativeHeaders implements Action<Task> {
        private final Provider<Directory> generatedHeadersDirectory;

        @Inject
        public abstract FileSystemOperations getFileSystemOperations();

        @Inject
        public RemoveGeneratedNativeHeaders(Provider<Directory> generatedHeadersDirectory) {
            this.generatedHeadersDirectory = generatedHeadersDirectory;
        }

        @Override
        public void execute(Task task) {
            getFileSystemOperations().delete(spec -> spec.delete(generatedHeadersDirectory));
        }
    }
}
