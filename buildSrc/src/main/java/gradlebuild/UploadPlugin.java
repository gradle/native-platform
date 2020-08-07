package gradlebuild;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.util.stream.Collectors;

/**
 * Uploads each publication of the project to bintray. Uses the bintray API directly rather than the bintray plugin, as the plugin does not
 * handle multiple packages per project.
 */
public class UploadPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getPlugins().apply(BasePublishPlugin.class);
        BintrayCredentials credentials = project.getExtensions().getByType(BintrayCredentials.class);

        project.getExtensions().configure(PublishingExtension.class, extension -> {
            extension.getPublications().withType(MavenPublication.class, publication -> {
                String capitalizedPublicationName = BasePublishPlugin.capitalize(publication.getName());
                TaskProvider<UpdatePackageMetadataTask> updateProvider = project.getTasks().register("updatePackage" + capitalizedPublicationName, UpdatePackageMetadataTask.class,
                    update -> update.setPublication(publication)
                );

                TaskProvider<PublishToMavenRepository> publishToLocalRepositoryTask = project.getTasks().named(BasePublishPlugin.publishTaskName(publication, BasePublishPlugin.LOCAL_FILE_REPOSITORY_NAME), PublishToMavenRepository.class);
                project.getTasks().register(uploadTaskName(publication), UploadTask.class, upload -> {
                    upload.setGroup("Upload");
                    upload.setDescription("Upload publication " + publication.getName());
                    upload.setLocalRepoDir(() -> new File(publishToLocalRepositoryTask.get().getRepository().getUrl()));
                    upload.setPublication(publication);
                    upload.dependsOn(updateProvider, publishToLocalRepositoryTask);
                });
            });
        });
        VariantsExtension variants = project.getExtensions().getByType(VariantsExtension.class);
        project.getTasks().register("publishToBintray", PublishToBintrayTask.class, publishToBintrayTask -> {
            publishToBintrayTask.setGroup("Upload");
            publishToBintrayTask.setDescription("Publishes the uploaded variants on Bintray");
            publishToBintrayTask.getGroupId().set(variants.getGroupId());
            publishToBintrayTask.getVersion().set(variants.getVersion());
            publishToBintrayTask.getArtifactIds().add(variants.getArtifactId());
            publishToBintrayTask.getArtifactIds().addAll(variants.getVariantNames()
                .map(variantNames -> variantNames.stream().map(it -> variants.getArtifactId().get() + "-" + it).collect(Collectors.toList())));
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

    public static String uploadTaskName(Publication publication) {
        return "uploadPackage" + BasePublishPlugin.capitalize(publication.getName());
    }
}
