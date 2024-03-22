package gradlebuild;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.jvm.tasks.ProcessResources;

public abstract class CargoPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        TaskProvider<CargoBuild> buildRustLib = project.getTasks().register("buildRustLib", CargoBuild.class, rust -> {
            rust.getSources().from(project.getLayout().files("Cargo.toml", "Cargo.lock", "src/main/rust"));
            rust.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("rust"));
        });


        project.getTasks().named("processResources", ProcessResources.class, processResources ->
            processResources.from(buildRustLib.flatMap(it -> it.getDestinationDirectory().file("debug/libfile_events_rust.dylib")), spec -> spec.into(String.join(
                "/",
                project.getGroup().toString().replace(".", "/"),
                "platform",
                "osx-aarch64"
            )
        )
        ));
    }
}
