import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Concatenates JNI headers generated during Java compilation into a single file,
 * for easier consumption by the native code.
 */
public abstract class ConcatenateJniHeaders extends DefaultTask {

    /**
     * The directory containing the generated JNI headers.
     *
     * Contains one header file per class with a native method.
     */
    @InputFiles
    public abstract ConfigurableFileCollection getJniHeaders();

    /**
     * The location of the concatenated header file.
     */
    @OutputFile
    public abstract RegularFileProperty getGeneratedNativeHeader();

    @TaskAction
    public void concatenate() throws IOException {
        List<File> jniHeaders = new ArrayList<>(getJniHeaders().getFiles());
        jniHeaders.sort(Comparator.comparing(File::getName));
        Path outputFile = getGeneratedNativeHeader().get().getAsFile().toPath();
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            for (File header : jniHeaders) {
                List<String> lines = Files.readAllLines(header.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    writer.append(line);
                    writer.append("\n");
                }
                writer.append("\n");
            };
        }
    }
}
