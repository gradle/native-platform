package gradlebuild;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.publish.PublishingExtension;

import java.util.stream.Stream;

public class PublishPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(BasePublishPlugin.class);
        PublishRepositoryCredentials credentials = project.getExtensions().getByType(PublishRepositoryCredentials.class);

        project.getExtensions().configure(
            PublishingExtension.class,
            extension -> {
                RepositoryHandler repositories = extension.getRepositories();
                Stream.of(VersionDetails.ReleaseRepository.values())
                    .forEach(repository -> repositories.maven(repo -> {
                        repo.setUrl(repository.getUrl());
                        repo.setName(repository.name());
                        repo.credentials(passwordCredentials -> {
                            passwordCredentials.setUsername(credentials.getUserName());
                            passwordCredentials.setPassword(credentials.getApiKey());
                        });
                    }));
            });
    }
}
