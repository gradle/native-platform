package gradlebuild;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Optional;

public class VersionDetails {

    public enum RepositoryType {
        Maven, Bintray
    }

    public enum ReleaseRepository {
        GradleRepoSnapshots("https://repo.gradle.org/gradle/ext-snapshots-local", RepositoryType.Maven),
        GradleRepoReleases("https://repo.gradle.org/gradle/ext-releases-local", RepositoryType.Maven),
        BintrayReleases("https://dl.bintray.com/adammurdoch/maven", RepositoryType.Bintray);

        private final String url;
        private final RepositoryType type;

        ReleaseRepository(String url, RepositoryType type) {
            this.url = url;
            this.type = type;
        }

        public String getUrl() {
            return url;
        }

        public RepositoryType getType() {
            return type;
        }
    }

    public enum BuildType {
        Dev(null),
        Snapshot(ReleaseRepository.GradleRepoSnapshots),
        Alpha(ReleaseRepository.GradleRepoReleases),
        Milestone(ReleaseRepository.BintrayReleases),
        Release(ReleaseRepository.BintrayReleases);

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
