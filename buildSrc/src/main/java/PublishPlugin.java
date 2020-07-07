import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;

public class PublishPlugin implements Plugin<Project> {
    private static final String SNAPSHOT_REPOSITORY_NAME = "Snapshot";
    private static final String LOCAL_FILE_REPOSITORY_NAME = "LocalFile";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(BasePublishPlugin.class);
        final BintrayCredentials credentials = project.getExtensions().getByType(BintrayCredentials.class);

        project.getExtensions().configure(
                PublishingExtension.class,
                extension -> {
                    RepositoryHandler repositories = extension.getRepositories();
                    repositories.maven(repo -> {
                        repo.setUrl(ReleasePlugin.SNAPSHOT_REPOSITORY_URL);
                        repo.setName(SNAPSHOT_REPOSITORY_NAME);
                        repo.credentials(passwordCredentials -> {
                            passwordCredentials.setUsername(credentials.getUserName());
                            passwordCredentials.setPassword(credentials.getApiKey());
                        });
                    });
                    repositories.maven(repo -> {
                        repo.setName(LOCAL_FILE_REPOSITORY_NAME);
                        repo.setUrl(project.getRootProject().file("build/repo"));
                    });
                });
    }

    public static String uploadTaskName(Publication publication) {
        return BasePublishPlugin.publishTaskName(publication, SNAPSHOT_REPOSITORY_NAME);
    }

    public static String uploadToLocalRepositoryTaskName(Publication publication) {
        return BasePublishPlugin.publishTaskName(publication, LOCAL_FILE_REPOSITORY_NAME);
    }
}
