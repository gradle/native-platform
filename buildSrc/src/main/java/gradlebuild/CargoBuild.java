package gradlebuild;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;

@CacheableTask
public abstract class CargoBuild extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    @IgnoreEmptyDirectories
    public abstract ConfigurableFileCollection getSources();

    @OutputDirectory
    public abstract DirectoryProperty getDestinationDirectory();

    @Inject
    public abstract ExecOperations getExecOperations();

    @TaskAction
    public void cargoBuild() {
        String userHome = System.getProperty("user.home");
        File cargo = new File(userHome, ".cargo/bin/cargo");
        getExecOperations().exec(spec -> spec.commandLine(
            cargo.getAbsolutePath(), "build",
            "--config", "build.target-dir=\"" + getDestinationDirectory().get().getAsFile().getAbsolutePath() + "\""
        ));
    }
}
