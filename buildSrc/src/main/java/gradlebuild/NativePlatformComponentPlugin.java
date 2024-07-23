package gradlebuild;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

public abstract class NativePlatformComponentPlugin implements Plugin<Project> {
    public static final String GROUP_ID = "net.rubygrapefruit";
    private static final String TEST_DIRECTORY_SYSTEM_PROPERTY = "test.directory";

    @Override
    public void apply(Project project) {
        project.getRootProject().getPlugins().apply(BasePlugin.class);
        project.setGroup(GROUP_ID);

        project.getPlugins().apply(JavaPlugin.class);
        project.getRepositories().mavenCentral();

        project.getPlugins().apply(ReleasePlugin.class);

        configureTestTasks(project);
        configureJavaCompatibility(project);

        project.getTasks().withType(GroovyCompile.class).configureEach(task -> task.getOptions().setIncremental(true));
    }

    private void configureJavaCompatibility(Project project) {
        // Java 9 and later don't support targetting Java 5
        JavaVersion compatibility = JavaVersion.current().isJava9Compatible() ? JavaVersion.VERSION_1_6 : JavaVersion.VERSION_1_5;

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.setSourceCompatibility(compatibility);
        java.setTargetCompatibility(compatibility);
    }

    private void configureTestTasks(Project project) {
        JavaPluginConvention javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        project.getTasks().withType(Test.class).configureEach(test -> {
            test.systemProperty(TEST_DIRECTORY_SYSTEM_PROPERTY, project.getLayout().getBuildDirectory().dir("test files").map(dir -> dir.getAsFile().getAbsoluteFile()).get());

            // Reconfigure the classpath for the test task here, so we can use dependency substitution on `testRuntimeClasspath`
            // to test an external dependency.
            // We omit `sourceSets.main.output` here and replace it with a test dependency on `project(':')` further down.
            Configuration testRuntimeClasspath = project.getConfigurations().getByName("testRuntimeClasspath");
            SourceSetOutput testOutput = javaPluginConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME).getOutput();
            test.setClasspath(project.files(testRuntimeClasspath, testOutput));

            Provider<String> agentName = project.getProviders().gradleProperty("agentName").forUseAtConfigurationTime();
            test.systemProperty("agentName", agentName.getOrElse("Unknown"));
        });

        Stream.of("xfs", "btrfs").forEach(fileSystemType ->
            project.getTasks().register("test" + capitalize(fileSystemType), Test.class, test -> {
                File mountPoint = new File("/" + fileSystemType);
                test.systemProperty(TEST_DIRECTORY_SYSTEM_PROPERTY, new File(mountPoint, "native-platform/test files").getAbsolutePath());
                test.doFirst(new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        Preconditions.checkArgument(mountPoint.isDirectory(),
                            "Mount point for special file system %s is not a directory", mountPoint);
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        String findmntPath = Stream.of("/bin/findmnt", "/usr/bin/findmnt")
                            .filter(path -> new File(path).isFile())
                            .findAny()
                            .orElseThrow(() -> new IllegalArgumentException("findmnt not found, make sure it is installed"));
                        getExecOperations().exec(spec -> {
                            spec.commandLine(
                                findmntPath,
                                "-n",  "-o", "FSTYPE", "-T", mountPoint);
                            spec.setStandardOutput(outputStream);
                        }).assertNormalExitValue();
                        String detectedFileSystemType = new String(outputStream.toByteArray(), StandardCharsets.UTF_8).trim();
                        Preconditions.checkArgument(fileSystemType.equals(detectedFileSystemType),
                            "File system mounted at %s is not %s but %s", mountPoint, fileSystemType, detectedFileSystemType);
                    }
                });
            }));

        // We need to add the root project to testImplementation manually, since we changed the wiring
        // for the test task to not use sourceSets.main.output.
        // This allows using dependency substitution for the root project.
        DependencyHandler dependencies = project.getDependencies();
        project.getDependencies().add("testImplementation", project.getDependencies().project(ImmutableMap.of("path", project.getPath())));
        dependencies.add("testImplementation", "org.spockframework:spock-core:1.3-groovy-2.5");
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    private static String capitalize(String name) {
        StringBuilder builder = new StringBuilder(name);
        builder.setCharAt(0, Character.toUpperCase(builder.charAt(0)));
        return builder.toString();
    }
}
