import com.google.common.collect.ImmutableList;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * Adds arguments for <pre>javac</pre>, so that JNI headers are generated.
 *
 * For each class with a native method, a separate header file is created.
 */
public abstract class CompileNativeHeaders implements CommandLineArgumentProvider {

    /**
     * The directory to generate the JNI headers to.
     */
    @OutputDirectory
    public abstract DirectoryProperty getGeneratedHeadersDirectory();

    @Override
    public Iterable<String> asArguments() {
        return ImmutableList.of("-h", getGeneratedHeadersDirectory().get().getAsFile().getAbsolutePath());
    }
}
