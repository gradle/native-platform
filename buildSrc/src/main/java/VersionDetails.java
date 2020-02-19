import javax.inject.Inject;

public class VersionDetails {

    public enum ReleaseRepository {
        None, Snapshots, Releases
    }

    public enum BuildType {
        Dev(ReleaseRepository.None),
        Snapshot(ReleaseRepository.Snapshots),
        Alpha(ReleaseRepository.Snapshots),
        Milestone(ReleaseRepository.Releases),
        Release(ReleaseRepository.Releases);

        private final ReleaseRepository releaseRepository;

        BuildType(ReleaseRepository releaseRepository) {
            this.releaseRepository = releaseRepository;
        }

        ReleaseRepository getReleaseRepository() {
            return releaseRepository;
        }
    }

    private String nextVersion;
    private String nextSnapshot;
    private String nextAlphaPostfix;
    private final BuildType buildType;

    @Inject
    public VersionDetails(BuildType buildType) {
        this.buildType = buildType;
    }

    public boolean isUseRepo() {
        return buildType.releaseRepository != ReleaseRepository.None;
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

    public String getNextAlphaPostfix() {
        return nextAlphaPostfix;
    }

    public void setNextAlphaPostfix(String nextAlphaPostfix) {
        this.nextAlphaPostfix = nextAlphaPostfix;
    }
}
