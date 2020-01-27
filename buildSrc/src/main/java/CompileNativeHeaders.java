import com.google.common.collect.ImmutableList;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.process.CommandLineArgumentProvider;

public abstract class CompileNativeHeaders implements CommandLineArgumentProvider {

    @OutputDirectory
    public abstract DirectoryProperty getGeneratedHeadersDirectory();

    @Override
    public Iterable<String> asArguments() {
        return ImmutableList.of("-h", getGeneratedHeadersDirectory().get().getAsFile().getAbsolutePath());
    }
}
