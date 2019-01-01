import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskAction;

import java.io.ByteArrayInputStream;
import java.net.URI;

public class UpdatePackageMetadataTask extends BintrayTask {
    private MavenPublication publication;

    public MavenPublication getPublication() {
        return publication;
    }

    public void setPublication(MavenPublication publication) {
        this.publication = publication;
    }

    @TaskAction
    void update() throws Exception {
        System.out.println("Updating publication " + publication.getName() + " as " + publication.getGroupId() + ":" + publication.getArtifactId() + ":" + publication.getVersion());
        String content = "{ \"vcs_url\": \"https://github.com/adammurdoch/native-platform.git\", \"website_url\": \"https://github.com/adammurdoch/native-platform\", \"licenses\": [\"Apache-2.0\"] }";
        byte[] bytes = content.getBytes();
        URI url = new URI("https://api.bintray.com/packages/adammurdoch/maven/" + publication.getGroupId() + ":" + publication.getArtifactId());
        patch(url, bytes.length, new ByteArrayInputStream(bytes));
    }
}
