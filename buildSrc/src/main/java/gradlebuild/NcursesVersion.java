package gradlebuild;

import org.gradle.api.Named;

public enum NcursesVersion implements Named {
    NCURSES_5("5"), NCURSES_6("6");

    private final String versionNumber;

    NcursesVersion(String versionNumber) {
        this.versionNumber = versionNumber;
    }

    @Override
    public String getName() {
        return "ncurses" + versionNumber;
    }
}
