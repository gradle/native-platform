package gradlebuild;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.authentication.http.BasicAuthentication;
import org.gradle.plugins.signing.Sign;
import org.gradle.plugins.signing.SigningExtension;

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

        project.getPlugins().apply(PublishPlugin.class);
        project.setVersion(versions.getVersion());

        // Use authenticated snapshot/release repo while building a test distribution during snapshot/release
        final PublishRepositoryCredentials credentials = project.getExtensions().getByType(PublishRepositoryCredentials.class);

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

        project.getPlugins().apply("signing");
        boolean signArtifacts = versions.isUseRepo();
        project.getTasks().withType(Sign.class).configureEach(sign -> {
            sign.setEnabled(signArtifacts);
        });
        project.getExtensions().configure(SigningExtension.class, signing -> {
            signing.useInMemoryPgpKeys(System.getenv("PGP_SIGNING_KEY"), System.getenv("PGP_SIGNING_KEY_PASSPHRASE"));
            project.getExtensions().getByType(PublishingExtension.class).getPublications().configureEach(publication -> {
                if (signArtifacts) {
                    signing.sign(publication);
                }
            });
        });
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
                String uploadTaskName = releaseRepository
                    .map(repository -> PublishPlugin.publishTaskName(publication, repository.name()))
                    .orElse(PublishPlugin.publishTaskName(publication, PublishPlugin.LOCAL_FILE_REPOSITORY_NAME));
                TaskProvider<Task> uploadTask = project.getTasks().named(uploadTaskName);
                if (PublishPlugin.isMainPublication(publication)) {
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
