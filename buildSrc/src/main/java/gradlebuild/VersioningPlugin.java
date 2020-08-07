package gradlebuild;

import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.ProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class VersioningPlugin implements Plugin<Project> {
    private static final Logger LOGGER = LoggerFactory.getLogger(VersioningPlugin.class);

    private static final DateTimeFormatter SNAPSHOT_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssZ", Locale.US).withZone(ZoneOffset.UTC);
    private static final String BUILD_RECEIPT_NAME = "build-receipt.properties";
    private static final String BUILD_TIMESTAMP_PROPERTY = "buildTimestamp";
    public static final String NEXT_VERSION_PROPERTY_NAME = "nextVersion";
    public static final String NEXT_MILESTONE_PROPERTY_NAME = "nextMilestone";
    private static final String ALPHA_VERSION_PROPERTY_NAME = "alpha";

    @Override
    public void apply(Project project) {
        VersionDetails.BuildType buildType = determineBuildType(project);
        if (buildType != VersionDetails.BuildType.Dev && JavaVersion.current() != JavaVersion.VERSION_1_8) {
            throw new RuntimeException("Java 8 is required to build a release of native-platform. Later versions are not supported.");
        }
        String buildTimestamp = determineBuildTimestamp(project);
        writeBuildTimestamp(buildTimestamp, project);
        String version = determineVersion(buildType, buildTimestamp);
        project.getExtensions().create("versions", VersionDetails.class, buildType, version);
    }

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    private String determineVersion(VersionDetails.BuildType buildType, String buildTimestamp) {
        String nextVersion = getGradleProperty(NEXT_VERSION_PROPERTY_NAME);
        switch (buildType) {
            case Release:
                return nextVersion;
            case Milestone:
                return nextVersion + "-milestone-" + getGradleProperty(NEXT_MILESTONE_PROPERTY_NAME);
            case Alpha:
                return nextVersion + "-" + getGradleProperty(ALPHA_VERSION_PROPERTY_NAME);
            case Snapshot:
                return nextVersion + "-snapshot-" + buildTimestamp;
            case Dev:
                return nextVersion + "-dev";
            default:
                throw new AssertionError("Unknown build type " + buildType);
        }
    }

    private String getGradleProperty(String propertyName) {
        String value = getProviderFactory().gradleProperty(propertyName).forUseAtConfigurationTime().getOrNull();
        if (value == null || value.isEmpty()) {
            throw new UnsupportedOperationException("No value for Gradle property '" + propertyName + "' specified");
        }
        return value;
    }

    private void writeBuildTimestamp(String buildTimestamp, Project project) {
        File buildReceiptFile = project.getRootProject().file(BUILD_RECEIPT_NAME);
        try (OutputStream outputStream = Files.newOutputStream(buildReceiptFile.toPath())) {
            Properties properties = new Properties();
            properties.setProperty(BUILD_TIMESTAMP_PROPERTY, buildTimestamp);
            properties.store(outputStream, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String determineBuildTimestamp(Project project) {
        File buildReceipt = new File(project.file("incoming-distributions"), BUILD_RECEIPT_NAME);
        if (getProviderFactory().gradleProperty("ignoreIncomingBuildReceipt").forUseAtConfigurationTime().isPresent() || !buildReceipt.isFile()) {
            return ZonedDateTime.now().format(SNAPSHOT_TIMESTAMP_FORMATTER);
        }
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(buildReceipt.toPath())) {
            properties.load(inputStream);
            String buildTimestamp = properties.getProperty(BUILD_TIMESTAMP_PROPERTY);
            LOGGER.warn("Using build timestamp from incoming build receipt: {}", buildTimestamp);
            return properties.getProperty("buildTimestamp");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private VersionDetails.BuildType determineBuildType(Project project) {
        boolean release = project.hasProperty("release");
        boolean milestone = project.hasProperty("milestone");
        boolean alpha = project.hasProperty(ALPHA_VERSION_PROPERTY_NAME);
        boolean snapshot = project.hasProperty("snapshot");

        Set<VersionDetails.BuildType> enabledBuildTypes = EnumSet.noneOf(VersionDetails.BuildType.class);
        if (release) {
            enabledBuildTypes.add(VersionDetails.BuildType.Release);
        }
        if (milestone) {
            enabledBuildTypes.add(VersionDetails.BuildType.Milestone);
        }
        if (alpha) {
            enabledBuildTypes.add(VersionDetails.BuildType.Alpha);
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
}
