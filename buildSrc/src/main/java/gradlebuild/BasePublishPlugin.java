package gradlebuild;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

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

        TaskContainer tasks = project.getTasks();
        TaskProvider<Jar> jarTask = project.getTasks().named("jar", Jar.class);
        TaskProvider<Jar> sourceZipTask = tasks.register("sourceZip", Jar.class, jar -> jar.getArchiveClassifier().set("sources"));
        TaskProvider<Jar> javadocZipTask = tasks.register("javadocZip", Jar.class, jar -> jar.getArchiveClassifier().set("javadoc"));
        project.getExtensions().configure(
            PublishingExtension.class,
            extension -> extension.getPublications().create("main", MavenPublication.class, main -> {
                main.artifact(jarTask.get());
                main.artifact(sourceZipTask.get());
                main.artifact(javadocZipTask.get());
            }));
        project.afterEvaluate(ignored -> {
            project.getExtensions().configure(
                PublishingExtension.class,
                extension -> extension.getPublications().named("main", MavenPublication.class, main -> {
                    main.setGroupId(project.getGroup().toString());
                    main.setArtifactId(jarTask.get().getArchiveBaseName().get());
                    main.setVersion(project.getVersion().toString());
                }));
        });
    }

    public static String getArchivesBaseName(Project project) {
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
