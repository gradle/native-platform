package gradlebuild;

import org.gradle.api.provider.Property;

public abstract class NativePlatformVersionExtension {
    public abstract Property<String> getVersionClassPackageName();
    public abstract Property<String> getVersionClassName();
}
