import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.publish.Publication;

public class BasePublishPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply("maven-publish");
        final BintrayCredentials credentials = project.getExtensions().create("bintray", BintrayCredentials.class);
        if (project.hasProperty("bintrayUserName")) {
            credentials.setUserName(project.property("bintrayUserName").toString());
        }
        if (project.hasProperty("bintrayApiKey")) {
            credentials.setApiKey(project.property("bintrayApiKey").toString());
        }
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
