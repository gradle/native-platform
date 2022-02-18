package gradlebuild.actions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;

import java.util.ArrayList;
import java.util.List;

public final class RegisterJniTestTask implements Action<Project> {
    @Override
    public void execute(Project project) {
        TaskProvider<Test> testJni = project.getTasks().register("testJni", Test.class, task -> {
            task.setGroup("verification");

            // See https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/clopts002.html
            task.jvmArgs("-Xcheck:jni");

            // Only run tests that have the category
            task.useJUnit(jUnitOptions ->
                jUnitOptions.includeCategories("net.rubygrapefruit.platform.testfixture.JniChecksEnabled")
            );
            // Check standard output for JNI warnings and fail if we find anything
            DetectJniWarnings detectJniWarnings = new DetectJniWarnings();
            task.addTestListener(detectJniWarnings);
            task.getLogging().addStandardOutputListener(detectJniWarnings);
            task.doLast(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    List<String> detectedWarnings = detectJniWarnings.getDetectedWarnings();
                    if (!detectedWarnings.isEmpty()) {
                        throw new RuntimeException(String.format(
                            "Detected JNI check warnings on standard output while executing tests:\n - %s",
                            String.join("\n - ", detectedWarnings)
                        ));
                    }
                }
            });
        });
        project.getTasks().named("check", check -> check.dependsOn(testJni));
    }

    private static class DetectJniWarnings implements TestListener, StandardOutputListener {
        private String currentTest;
        private List<String> detectedWarnings = new ArrayList<>();

        @Override
        public void beforeSuite(TestDescriptor testDescriptor) {}

        @Override
        public void afterSuite(TestDescriptor testDescriptor, TestResult testResult) {}

        @Override
        public void beforeTest(TestDescriptor testDescriptor) {
            currentTest = testDescriptor.getClassName() + "." + testDescriptor.getDisplayName();
        }

        @Override
        public void afterTest(TestDescriptor testDescriptor, TestResult testResult) {
            currentTest = null;
        }

        @Override
        public void onOutput(CharSequence message) {
            if (currentTest != null && message.toString().startsWith("WARNING")) {
                detectedWarnings.add(String.format("%s (test: %s)", message, currentTest));
            }
        }

        public List<String> getDetectedWarnings() {
            return detectedWarnings;
        }
    }
}
