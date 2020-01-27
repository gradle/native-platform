import org.gradle.api.file.DirectoryProperty;

public interface JniExtension {
    DirectoryProperty getGeneratedHeaderDirectory();
}
