import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.authentication.http.BasicAuthentication;

/**
 * Takes care of adding tasks and configurations to build developer distributions and releases.
 */
public class ReleasePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply(UploadPlugin.class);
        final BintrayCredentials credentials = project.getExtensions().getByType(BintrayCredentials.class);

        // Use authenticated bintray repo while building a test distribution during snapshot/release
        if (credentials.getUserName() != null && credentials.getApiKey() != null) {
            project.getRepositories().maven(new Action<MavenArtifactRepository>() {
                @Override
                public void execute(MavenArtifactRepository repo) {
                    repo.setUrl("https://dl.bintray.com/adammurdoch/maven");
                    repo.getCredentials().setUsername(credentials.getUserName());
                    repo.getCredentials().setPassword(credentials.getApiKey());
                    repo.getAuthentication().create("basic", BasicAuthentication.class);
                }
            });
        }
    }
}
