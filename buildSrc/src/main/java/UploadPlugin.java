import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.internal.ErroringAction;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * Uploads each publication of the project to bintray. Uses the bintray API directly rather than the bintray plugin, as the plugin does not
 * handle multiple packages per project.
 */
public class UploadPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getPlugins().apply(BasePublishPlugin.class);
        final BintrayCredentials credentials = project.getExtensions().getByType(BintrayCredentials.class);

        final Callable<File> repoDir = () -> project.getRootProject().getLayout().getBuildDirectory().dir("repo").get().getAsFile();
        project.getExtensions().configure(PublishingExtension.class, extension -> {
            extension.getRepositories().maven(new ErroringAction<MavenArtifactRepository>() {
                @Override
                public void doExecute(MavenArtifactRepository repo) throws Exception {
                    repo.setUrl(repoDir.call());
                }
            });
            extension.getPublications().withType(MavenPublication.class, publication -> {
                String capitalizedPublicationName = BasePublishPlugin.capitalize(publication.getName());
                UpdatePackageMetadataTask update = project.getTasks().create("updatePackage" + capitalizedPublicationName, UpdatePackageMetadataTask.class);
                update.setPublication(publication);

                UploadTask upload = project.getTasks().create("uploadPackage" + capitalizedPublicationName, UploadTask.class);
                upload.setGroup("Upload");
                upload.setDescription("Upload publication " + publication.getName());
                upload.setLocalRepoDir(repoDir);
                upload.setPublication(publication);
                upload.dependsOn(update);
                if (!BasePublishPlugin.isMainPublication(publication)) {
                    Task uploadLifecycle = project.getTasks().maybeCreate("uploadJni");
                    uploadLifecycle.setGroup("Upload");
                    uploadLifecycle.setDescription("Upload all JNI publications");
                    uploadLifecycle.dependsOn(upload);
                }

            });
        });
        project.getGradle().getTaskGraph().whenReady(graph -> {
            for (Task task : graph.getAllTasks()) {
                if (task instanceof BintrayTask) {
                    credentials.assertPresent();
                    BintrayTask bintrayTask = (BintrayTask) task;
                    bintrayTask.setUserName(credentials.getUserName());
                    bintrayTask.setApiKey(credentials.getApiKey());
                }
            }
        });
    }
}
