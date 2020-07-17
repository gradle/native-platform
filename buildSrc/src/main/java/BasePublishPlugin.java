import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;

import java.io.File;
import java.util.concurrent.Callable;

public class BasePublishPlugin implements Plugin<Project> {
    public static final String LOCAL_FILE_REPOSITORY_NAME = "LocalFile";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply("maven-publish");
        BintrayCredentials credentials = project.getExtensions().create("bintray", BintrayCredentials.class);
        VariantsExtension variants = project.getExtensions().create("variants", VariantsExtension.class);
        variants.getGroupId().convention(project.provider(() -> project.getGroup().toString()));
        variants.getArtifactId().convention(project.provider(() -> getArchivesBaseName(project)));
        variants.getVersion().convention(project.provider(() -> project.getVersion().toString()));

        if (project.hasProperty("bintrayUserName")) {
            credentials.setUserName(project.property("bintrayUserName").toString());
        }
        if (project.hasProperty("bintrayApiKey")) {
            credentials.setApiKey(project.property("bintrayApiKey").toString());
        }
        Callable<File> localRepoDir = () -> getLocalRepoDirectory(project);
        project.getExtensions().configure(PublishingExtension.class,
            extension -> extension.getRepositories().maven(repo -> {
                repo.setName(LOCAL_FILE_REPOSITORY_NAME);
                repo.setUrl(localRepoDir);
            })
        );
    }

    private static String getArchivesBaseName(Project project) {
        BasePluginConvention convention = project.getConvention().findPlugin(BasePluginConvention.class);
        return convention != null ? convention.getArchivesBaseName() : project.getName();
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
