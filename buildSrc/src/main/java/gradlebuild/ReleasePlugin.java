package gradlebuild;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.authentication.http.BasicAuthentication;

import java.util.Optional;

/**
 * Takes care of adding tasks and configurations to build developer distributions and releases.
 */
public class ReleasePlugin implements Plugin<Project> {
    private static final String UPLOAD_MAIN_TASK_NAME = "uploadMain";
    private static final String UPLOAD_JNI_TASK_NAME = "uploadJni";
    private static final String UPLOAD_NCURSES_JNI_TASK_NAME = "uploadNcursesJni";

    @Override
    public void apply(Project project) {
        Project rootProject = project.getRootProject();
        rootProject.getPlugins().apply(VersioningPlugin.class);
        VersionDetails versions = rootProject.getExtensions().getByType(VersionDetails.class);
        project.getGradle().getTaskGraph().whenReady(graph -> {
            String projectPath = project.getPath();
            if (graph.hasTask(projectPath + ":" + UPLOAD_MAIN_TASK_NAME) || graph.hasTask(projectPath + ":" + UPLOAD_JNI_TASK_NAME) || graph.hasTask(projectPath + ":" + UPLOAD_NCURSES_JNI_TASK_NAME)) {
                project.getLogger().lifecycle("##teamcity[buildStatus text='{build.status.text}, Published version {}']", versions.getVersion());
            }
        });

        project.getPlugins().apply(UploadPlugin.class);
        project.getPlugins().apply(PublishPlugin.class);
        project.setVersion(versions.getVersion());

        // Use authenticated snapshot/bintray repo while building a test distribution during snapshot/release
        final BintrayCredentials credentials = project.getExtensions().getByType(BintrayCredentials.class);

        versions.getReleaseRepository().ifPresent(releaseRepository -> {
            credentials.assertPresent();
            project.getRepositories().maven(repo -> {
                repo.setUrl(releaseRepository.getUrl());
                repo.getCredentials().setUsername(credentials.getUserName());
                repo.getCredentials().setPassword(credentials.getApiKey());
                repo.getAuthentication().create("basic", BasicAuthentication.class);
            });
        });

        addUploadLifecycleTasks(project, versions.getReleaseRepository());
    }

    private void addUploadLifecycleTasks(Project project, Optional<VersionDetails.ReleaseRepository> releaseRepository) {
        Task uploadMainLifecycle = project.getTasks().maybeCreate(UPLOAD_MAIN_TASK_NAME);
        uploadMainLifecycle.setGroup("Upload");
        uploadMainLifecycle.setDescription("Upload Main publication");

        Task uploadJniLifecycle = project.getTasks().maybeCreate(UPLOAD_JNI_TASK_NAME);
        uploadJniLifecycle.setGroup("Upload");
        uploadJniLifecycle.setDescription("Upload all JNI publications");

        Task uploadNcursesJniLifecycle = project.getTasks().maybeCreate(UPLOAD_NCURSES_JNI_TASK_NAME);
        uploadNcursesJniLifecycle.setGroup("Upload");
        uploadNcursesJniLifecycle.setDescription("Upload only ncurses5/6 JNI publications");

        project.getExtensions().configure(
            PublishingExtension.class,
            extension -> extension.getPublications().withType(MavenPublication.class, publication -> {
                String uploadTaskName = releaseRepository.map(repository ->
                    repository.getType() == VersionDetails.RepositoryType.Maven
                        ? BasePublishPlugin.publishTaskName(publication, repository.name())
                        : UploadPlugin.uploadTaskName(publication))
                    .orElse(BasePublishPlugin.publishTaskName(publication, BasePublishPlugin.LOCAL_FILE_REPOSITORY_NAME));
                TaskProvider<Task> uploadTask = project.getTasks().named(uploadTaskName);
                if (BasePublishPlugin.isMainPublication(publication)) {
                    uploadMainLifecycle.dependsOn(uploadTask);
                } else {
                    uploadJniLifecycle.dependsOn(uploadTask);
                    if (uploadTaskName.contains("ncurses")) {
                        uploadNcursesJniLifecycle.dependsOn(uploadTask);
                    }
                }
            }));
    }
}
