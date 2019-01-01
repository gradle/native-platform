import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.authentication.http.BasicAuthentication;

/**
 * Takes care of adding tasks and configurations to build developer distributions and releases.
 */
public class ReleasePlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        project.getPlugins().apply(UploadPlugin.class);

        boolean release = project.hasProperty("release");
        boolean milestone = project.hasProperty("milestone");
        if (release && milestone) {
            throw new UnsupportedOperationException("Cannot build release and milestone in same build.");
        }
        VersionDetails.BuildType buildType = VersionDetails.BuildType.Dev;
        if (release) {
            buildType = VersionDetails.BuildType.Release;
        } else if (milestone) {
            buildType = VersionDetails.BuildType.Milestone;
        }
        VersionDetails versions = project.getExtensions().create("versions", VersionDetails.class, buildType);
        project.setVersion(new VersionCalculator(versions, buildType));

        // Use authenticated bintray repo while building a test distribution during snapshot/release
        final BintrayCredentials credentials = project.getExtensions().getByType(BintrayCredentials.class);
        if (versions.isUseRepo()) {
            credentials.assertPresent();
            project.getRepositories().maven(new Action<MavenArtifactRepository>() {
                @Override
                public void execute(MavenArtifactRepository repo) {
                    repo.setUrl("https://dl.bintray.com/adammurdoch/maven");
                    repo.getCredentials().setUsername(credentials.getUserName());
                    repo.getCredentials().setPassword(credentials.getApiKey());
                    repo.getAuthentication().create("basic", BasicAuthentication.class);
                }
            });
        }
    }

    private static class VersionCalculator {
        private final VersionDetails release;
        private final VersionDetails.BuildType buildType;
        private String version;

        VersionCalculator(VersionDetails release, VersionDetails.BuildType buildType) {
            this.release = release;
            this.buildType = buildType;
        }

        @Override
        public String toString() {
            if (version == null) {
                String nextVersion = release.getNextVersion();
                if (nextVersion == null) {
                    throw new UnsupportedOperationException("Next version not specified.");
                }
                if (buildType == VersionDetails.BuildType.Release) {
                    version = nextVersion;
                } else if (buildType == VersionDetails.BuildType.Milestone) {
                    if (release.getNextSnapshot() == null) {
                        throw new UnsupportedOperationException("Next milestone not specified.");
                    }
                    version = nextVersion + "-milestone-" + release.getNextSnapshot();
                } else {
                    version = nextVersion + "-dev";
                }
            }
            return version;
        }
    }
}
