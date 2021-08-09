package gradlebuild;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Optional;

public class VersionDetails {
    private static final String GRADLE_INTERNAL_REPOSITORY_URL = System.getenv("GRADLE_INTERNAL_REPO_URL");
    private static final String GRADLE_REPOSITORY_URL = GRADLE_INTERNAL_REPOSITORY_URL == null ? "https://repo.gradle.org/gradle" : GRADLE_INTERNAL_REPOSITORY_URL;

    public enum ReleaseRepository {
        GradleRepoSnapshots("libs-snapshots-local"),
        GradleRepoReleases("libs-releases-local");

        private final String url;

        ReleaseRepository(String url) {
            this.url = GRADLE_REPOSITORY_URL + "/" + url;
        }

        public String getUrl() {
            return url;
        }
    }

    public enum BuildType {
        Dev(null),
        Snapshot(ReleaseRepository.GradleRepoSnapshots),
        Alpha(ReleaseRepository.GradleRepoReleases),
        Milestone(ReleaseRepository.GradleRepoReleases),
        Release(ReleaseRepository.GradleRepoReleases);

        private final ReleaseRepository releaseRepository;

        BuildType(@Nullable ReleaseRepository releaseRepository) {
            this.releaseRepository = releaseRepository;
        }

        Optional<ReleaseRepository> getReleaseRepository() {
            return Optional.ofNullable(releaseRepository);
        }
    }

    private final BuildType buildType;
    private final String version;

    @Inject
    public VersionDetails(BuildType buildType, String version) {
        this.buildType = buildType;
        this.version = version;
    }

    public boolean isUseRepo() {
        return buildType.getReleaseRepository().isPresent();
    }

    public Optional<ReleaseRepository> getReleaseRepository() {
        return buildType.getReleaseRepository();
    }

    public String getVersion() {
        return version;
    }
}
