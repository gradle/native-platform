import javax.inject.Inject;

public class VersionDetails {

    public enum BuildType {
        Dev, Snapshot, Milestone, Release
    }

    private String nextVersion;
    private String nextSnapshot;
    private final BuildType buildType;

    @Inject
    public VersionDetails(BuildType buildType) {
        this.buildType = buildType;
    }

    public boolean isUseRepo() {
        return buildType != BuildType.Dev;
    }

    public String getNextVersion() {
        return nextVersion;
    }

    public void setNextVersion(String nextVersion) {
        this.nextVersion = nextVersion;
    }

    public String getNextSnapshot() {
        return nextSnapshot;
    }

    public void setNextSnapshot(String nextSnapshot) {
        this.nextSnapshot = nextSnapshot;
    }
}
