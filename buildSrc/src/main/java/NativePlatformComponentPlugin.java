import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;

public class NativePlatformComponentPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getRootProject().getPlugins().apply(BasePlugin.class);
        project.getPlugins().apply(JavaPlugin.class);
        project.getRepositories().jcenter();
        project.setGroup("net.rubygrapefruit");

        // Java 9 and later don't support targetting Java 5
        JavaVersion compatibility = JavaVersion.current().isJava9Compatible() ? JavaVersion.VERSION_1_6 : JavaVersion.VERSION_1_5;

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.setSourceCompatibility(compatibility);
        java.setTargetCompatibility(compatibility);

        project.getPlugins().apply(ReleasePlugin.class);
    }
}
