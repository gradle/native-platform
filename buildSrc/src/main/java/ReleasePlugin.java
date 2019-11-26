import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.authentication.http.BasicAuthentication;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Takes care of adding tasks and configurations to build developer distributions and releases.
 */
public class ReleasePlugin implements Plugin<Project> {
    public static final String SNAPSHOT_REPOSITORY_URL = "https://repo.gradle.org/gradle/ext-snapshots-local";
    private static final String RELEASES_REPOSITORY_URL = "https://dl.bintray.com/adammurdoch/maven";

    private static final DateTimeFormatter SNAPSHOT_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssZ", Locale.US).withZone(ZoneOffset.UTC);

    @Override
    public void apply(final Project project) {
        project.getPlugins().apply(UploadPlugin.class);
        project.getPlugins().apply(PublishSnapshotPlugin.class);

        VersionDetails.BuildType buildType = determineBuildType(project);
        VersionDetails versions = project.getExtensions().create("versions", VersionDetails.class, buildType);
        project.setVersion(new VersionCalculator(versions, buildType));

        // Use authenticated snapshot/bintray repo while building a test distribution during snapshot/release
        final BintrayCredentials credentials = project.getExtensions().getByType(BintrayCredentials.class);
        if (versions.isUseRepo()) {
            credentials.assertPresent();
            String repositoryUrl = buildType == VersionDetails.BuildType.Snapshot
                    ? SNAPSHOT_REPOSITORY_URL
                    : RELEASES_REPOSITORY_URL;
            project.getRepositories().maven(repo -> {
                repo.setUrl(repositoryUrl);
                repo.getCredentials().setUsername(credentials.getUserName());
                repo.getCredentials().setPassword(credentials.getApiKey());
                repo.getAuthentication().create("basic", BasicAuthentication.class);
            });
        }
    }

    private VersionDetails.BuildType determineBuildType(Project project) {
        boolean snapshot = project.hasProperty("snapshot");
        boolean release = project.hasProperty("release");
        boolean milestone = project.hasProperty("milestone");

        Set<VersionDetails.BuildType> enabledBuildTypes = EnumSet.noneOf(VersionDetails.BuildType.class);
        if (release) {
            enabledBuildTypes.add(VersionDetails.BuildType.Release);
        }
        if (milestone) {
            enabledBuildTypes.add(VersionDetails.BuildType.Milestone);
        }
        if (snapshot) {
            enabledBuildTypes.add(VersionDetails.BuildType.Snapshot);
        }
        if (enabledBuildTypes.size() > 1) {
            throw new UnsupportedOperationException(
                    "Cannot build " +
                            enabledBuildTypes.stream()
                                    .map(Object::toString)
                                    .collect(Collectors.joining(" and ")) +
                            " in same build.");
        }
        return enabledBuildTypes.stream().findFirst().orElse(VersionDetails.BuildType.Dev);
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
                } else if (buildType == VersionDetails.BuildType.Snapshot) {
                    version = nextVersion + "-snapshot-" + ZonedDateTime.now().format(SNAPSHOT_TIMESTAMP_FORMATTER);
                } else {
                    version = nextVersion + "-dev";
                }
            }
            return version;
        }
    }
}
