import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;

public class PublishSnapshotPlugin implements Plugin<Project> {
    private static final String SNAPSHOT_REPOSITORY_NAME = "Snapshot";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(BasePublishPlugin.class);
        final BintrayCredentials credentials = project.getExtensions().getByType(BintrayCredentials.class);

        project.getExtensions().configure(PublishingExtension.class, extension -> {
            extension.getRepositories().maven(repo -> {
                repo.setUrl(ReleasePlugin.SNAPSHOT_REPOSITORY_URL);
                repo.setName(SNAPSHOT_REPOSITORY_NAME);
                repo.credentials(passwordCredentials -> {
                    passwordCredentials.setUsername(credentials.getUserName());
                    passwordCredentials.setPassword(credentials.getApiKey());
                });
            });

            extension.getPublications().withType(MavenPublication.class, publication -> {
                if (!BasePublishPlugin.isMainPublication(publication)) {
                    Task publishLifecycle = project.getTasks().maybeCreate("publishJni");
                    publishLifecycle.setGroup("Publish");
                    publishLifecycle.setDescription("Publish all JNI publications");
                    publishLifecycle.dependsOn(project.getTasks().named(BasePublishPlugin.publishTaskName(publication, SNAPSHOT_REPOSITORY_NAME)));
                }
            });
        });
    }
}
