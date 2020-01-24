import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.jvm.Jvm;

public abstract class GenerateJniHeader extends DefaultTask {

    @OutputFile
    abstract RegularFileProperty getHeaderFile();

    @Input
    abstract ListProperty<String> getClassNames();

    @Classpath
    abstract ConfigurableFileCollection getClasspath();

    @TaskAction
    public void generateHeaderFile() {
        getProject().exec(spec -> {
            spec.executable(Jvm.current().getExecutable("javah"));
            spec.args("-o", getHeaderFile().get().getAsFile().getAbsolutePath());
            spec.args("-classpath", getClasspath().getAsPath());
            spec.args(getClassNames().get());
        });
    }
}
