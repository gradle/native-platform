package gradlebuild;

import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Callable;

public class UploadTask extends BintrayTask {
    private MavenPublication publication;
    private Callable<File> localRepoDir;

    @Internal
    public MavenPublication getPublication() {
        return publication;
    }

    public void setPublication(MavenPublication publication) {
        this.publication = publication;
    }

    @Internal
    public Callable<File> getLocalRepoDir() {
        return localRepoDir;
    }

    public void setLocalRepoDir(Callable<File> localRepoDir) {
        this.localRepoDir = localRepoDir;
    }

    @TaskAction
    void upload() throws Exception {
        System.out.println("Uploading publication " + publication.getName() + " as " + publication.getGroupId() + ":" + publication.getArtifactId() + ":" + publication.getVersion());
        for (MavenArtifact artifact : publication.getArtifacts()) {
            upload(publication.getGroupId(), publication.getArtifactId(), publication.getVersion(), artifact.getClassifier(), artifact.getExtension());
        }
        upload(publication.getGroupId(), publication.getArtifactId(), publication.getVersion(), null, "pom");
    }

    private void upload(String groupId, String artifactId, String version, String classifier, String extension) throws Exception {
        String baseName = artifactId + "-" + version;
        if (classifier != null && !classifier.matches("\\s*")) {
            baseName += "-" + classifier;
        }
        baseName += "." + extension;
        upload(groupId, artifactId, version, baseName);
        upload(groupId, artifactId, version, baseName + ".sha1");
        upload(groupId, artifactId, version, baseName + ".md5");
    }

    private void upload(String groupId, String artifactId, String version, String baseName) throws Exception {
        System.out.println("Uploading file " + baseName);
        String mavenPath = groupId.replace(".", "/") + "/" + artifactId + "/" + version + "/" + baseName;
        File file = new File(localRepoDir.call(), mavenPath);
        if (!file.isFile()) {
            throw new IllegalArgumentException("Artifact " + baseName + " file " + file + " does not exist.");
        }
        try (FileInputStream instr = new FileInputStream(file)) {
            URI uploadUrl = uploadUrl(groupId, artifactId, mavenPath);
            put(uploadUrl, file.length(), instr);
        }
    }

    private URI uploadUrl(String groupId, String artifactId, String mavenPath) throws URISyntaxException {
        return new URI("https://api.bintray.com/maven/adammurdoch/maven/" + groupId + ":" + artifactId + "/" + mavenPath);
    }
}
