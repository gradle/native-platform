package gradlebuild;

import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

public interface VariantsExtension {
    Property<String> getGroupId();
    Property<String> getArtifactId();
    Property<String> getVersion();

    /**
     * List of names of extra variants of this artifact.
     *
     * May be empty.
     */
    SetProperty<String> getVariantNames();
}
