package gradlebuild;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

public class PublishPlugin implements Plugin<Project> {
    public static final String LOCAL_FILE_REPOSITORY_NAME = "LocalFile";
    private static final String PUBLISH_USER_NAME_PROPERTY = "publishUserName";
    private static final String PUBLISH_API_KEY_PROPERTY = "publishApiKey";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply("maven-publish");

        configureVariants(project);
        configureMainPublication(project);
        configureRepositories(project);
    }

    private void configureVariants(Project project) {
        VariantsExtension variants = project.getExtensions().create("variants", VariantsExtension.class);
        variants.getGroupId().convention(project.provider(() -> project.getGroup().toString()));
        variants.getArtifactId().convention(project.provider(() -> getArchivesBaseName(project)));
        variants.getVersion().convention(project.provider(() -> project.getVersion().toString()));
    }

    private void configureMainPublication(Project project) {
        TaskContainer tasks = project.getTasks();
        TaskProvider<Jar> jarTask = project.getTasks().named("jar", Jar.class);
        TaskProvider<Jar> sourceZipTask = tasks.register("sourceZip", Jar.class, jar -> jar.getArchiveClassifier().set("sources"));
        TaskProvider<Jar> javadocZipTask = tasks.register("javadocZip", Jar.class, jar -> jar.getArchiveClassifier().set("javadoc"));
        project.getExtensions().configure(
            PublishingExtension.class,
            extension -> extension.getPublications().create("main", MavenPublication.class, main -> {
                main.artifact(jarTask);
                main.artifact(sourceZipTask);
                main.artifact(javadocZipTask);
            }));
        // We need to configure the version in an afterEvaluate, since there is no lazy API, yet.
        project.afterEvaluate(ignored -> project.getExtensions().configure(
            PublishingExtension.class,
            extension -> extension.getPublications().named("main", MavenPublication.class, main -> {
                main.setGroupId(project.getGroup().toString());
                main.setArtifactId(jarTask.get().getArchiveBaseName().get());
                main.setVersion(project.getVersion().toString());
            })));
    }

    private void configureRepositories(Project project) {
        PublishRepositoryCredentials credentials = configureCredentials(project);
        project.getExtensions().configure(
            PublishingExtension.class,
            extension -> {
                RepositoryHandler repositories = extension.getRepositories();
                configureLocalPublishRepository(() -> getLocalRepoDirectory(project), repositories);
                configureRemotePublishRepositories(credentials, repositories);
            }
        );
    }

    private void configureLocalPublishRepository(Callable<File> localRepoDir, RepositoryHandler repositories) {
        repositories.maven(repo -> {
            repo.setName(LOCAL_FILE_REPOSITORY_NAME);
            repo.setUrl(localRepoDir);
        });
    }

    private void configureRemotePublishRepositories(PublishRepositoryCredentials credentials, RepositoryHandler repositories) {
        Stream.of(VersionDetails.ReleaseRepository.values())
            .forEach(repository -> repositories.maven(repo -> {
                repo.setUrl(repository.getUrl());
                repo.setName(repository.name());
                repo.credentials(passwordCredentials -> {
                    passwordCredentials.setUsername(credentials.getUserName());
                    passwordCredentials.setPassword(credentials.getApiKey());
                });
            }));
    }

    private PublishRepositoryCredentials configureCredentials(Project project) {
        PublishRepositoryCredentials credentials = project.getExtensions().create("publishRepository", PublishRepositoryCredentials.class);
        if (project.hasProperty(PUBLISH_USER_NAME_PROPERTY)) {
            credentials.setUserName(project.property(PUBLISH_USER_NAME_PROPERTY).toString());
        }
        if (project.hasProperty(PUBLISH_API_KEY_PROPERTY)) {
            credentials.setApiKey(project.property(PUBLISH_API_KEY_PROPERTY).toString());
        }
        return credentials;
    }

    public static String getArchivesBaseName(Project project) {
        BasePluginConvention convention = project.getConvention().findPlugin(BasePluginConvention.class);
        return convention.getArchivesBaseName();
    }

    public static File getLocalRepoDirectory(Project project) {
        return project.getRootProject().getLayout().getBuildDirectory().dir("repo").get().getAsFile();
    }

    public static String publishTaskName(Publication publication, String repository) {
        return "publish" + capitalize(publication.getName()) + "PublicationTo" + repository + "Repository";
    }

    static String capitalize(String name) {
        StringBuilder builder = new StringBuilder(name);
        builder.setCharAt(0, Character.toUpperCase(builder.charAt(0)));
        return builder.toString();
    }

    static boolean isMainPublication(Publication publication) {
        return publication.getName().equals("main");
    }
}
