package gradlebuild;

import org.gradle.api.Named;

public enum WindowsDistribution implements Named {
    WINDOWS_XP_OR_LOWER, WINDOWS_VISTA_OR_HIGHER;

    @Override
    public String getName() {
        return name();
    }
}
