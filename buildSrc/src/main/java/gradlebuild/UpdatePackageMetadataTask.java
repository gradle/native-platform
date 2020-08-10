package gradlebuild;

import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.ByteArrayInputStream;
import java.net.URI;

public class UpdatePackageMetadataTask extends BintrayTask {
    private MavenPublication publication;

    @Internal
    public MavenPublication getPublication() {
        return publication;
    }

    public void setPublication(MavenPublication publication) {
        this.publication = publication;
    }

    @TaskAction
    void update() throws Exception {
        System.out.println("Updating publication " + publication.getName() + " as " + publication.getGroupId() + ":" + publication.getArtifactId() + ":" + publication.getVersion());

        String packageName = publication.getGroupId() + ":" + publication.getArtifactId();
        String content = "{ \"name\": \"" + packageName + "\", \"vcs_url\": \"https://github.com/gradle/native-platform.git\", \"website_url\": \"https://github.com/gradle/native-platform\", \"licenses\": [\"Apache-2.0\"] }";
        byte[] bytes = content.getBytes();

        // Update the package details
        URI updateUrl = new URI("https://api.bintray.com/packages/adammurdoch/maven/" + packageName);
        try {
            patch(updateUrl, bytes.length, new ByteArrayInputStream(bytes));
        } catch (HttpException e) {
            if (e.getStatusCode() != 404) {
                throw e;
            }
            // Else, doesn't exist, try to create
            URI createUrl = new URI("https://api.bintray.com/packages/adammurdoch/maven/");
            post(createUrl, bytes.length, new ByteArrayInputStream(bytes));
        }
    }
}
