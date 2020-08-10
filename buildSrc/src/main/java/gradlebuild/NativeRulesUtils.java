package gradlebuild;

import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.platform.base.PlatformContainer;

public interface NativeRulesUtils {
    static void addPlatform(PlatformContainer platformContainer, String name, String os, String architecture) {
        platformContainer.create(name, NativePlatform.class, platform -> {
            platform.operatingSystem(os);
            platform.architecture(architecture);
        });
    }

}
