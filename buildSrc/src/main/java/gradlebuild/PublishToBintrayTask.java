package gradlebuild;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * Publishes the uploaded variants on Bintray.
 *
 * When uploading an artifact to bintray, it is not visible to the public from the start.
 * To make it visible, it needs to be published.
 * This tasks publishes a list of artifact ids for a certain group/version on Bintray.
 */
public abstract class PublishToBintrayTask extends BintrayTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublishToBintrayTask.class);

    @Input
    public abstract ListProperty<String> getArtifactIds();
    @Input
    public abstract Property<String> getGroupId();
    @Input
    public abstract Property<String> getVersion();

    @TaskAction
    public void publish() throws Exception {
        String groupId = getGroupId().get();
        String version = getVersion().get();
        for (String artifactId : getArtifactIds().get()) {
            LOGGER.warn("Publishing package {}:{}:{}", groupId, artifactId, version);
            String content = "{ \"publish_wait_for_secs\": -1 }";
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            post(publishUrl(groupId, artifactId, version), bytes.length, new ByteArrayInputStream(bytes));
        }
    }

    private static URI publishUrl(String groupId, String artifactId, String version) throws URISyntaxException {
        return new URI("https://api.bintray.com/content/adammurdoch/maven/" + groupId + ":" + artifactId + "/" + version + "/publish");
    }
}
