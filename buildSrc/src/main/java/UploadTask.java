import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskAction;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Callable;

public class UploadTask extends DefaultTask {
    private MavenPublication publication;
    private Callable<File> repoDir;
    private String userName;
    private String apiKey;

    public UploadTask() {
        dependsOn(new Callable<Task>() {
            @Override
            public Task call() {
                return getProject().getTasks().getByName("publish" + capitalize(publication.getName()) + "PublicationToMavenRepository");
            }
        });
    }

    public MavenPublication getPublication() {
        return publication;
    }

    public void setPublication(MavenPublication publication) {
        this.publication = publication;
    }

    public Callable<File> getRepoDir() {
        return repoDir;
    }

    public void setRepoDir(Callable<File> repoDir) {
        this.repoDir = repoDir;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @TaskAction
    void upload() throws Exception {
        System.out.println("Uploading publication " + publication.getName());
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
        File file = new File(repoDir.call(), mavenPath);
        if (!file.isFile()) {
            throw new IllegalArgumentException("Artifact " + baseName + " file " + file + " does not exist.");
        }
        try (FileInputStream instr = new FileInputStream(file)) {
            URL uploadUrl = uploadUrl(groupId, artifactId, mavenPath);
            upload(uploadUrl, file.length(), instr);
        }
    }

    private void upload(URL uploadUrl, long length, InputStream instr) throws IOException {
        System.setProperty("https.protocols", "TLSv1.2");
        HttpURLConnection connection = (HttpURLConnection) uploadUrl.openConnection();
        withCredentials(connection);
        connection.addRequestProperty("Content-Length", String.valueOf(length));
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        copyTo(instr, connection.getOutputStream());
        ByteArrayOutputStream collect = new ByteArrayOutputStream();
        copyTo(connection.getInputStream(), collect);
    }

    private URL uploadUrl(String groupId, String artifactId, String mavenPath) throws MalformedURLException {
        return new URL("https://api.bintray.com/maven/adammurdoch/maven/" + groupId + ":" + artifactId + "/" + mavenPath);
    }

    private void copyTo(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[1024 * 16];
        while (true) {
            int nread = inputStream.read(buffer);
            if (nread < 0) {
                break;
            }
            outputStream.write(buffer, 0, nread);
        }
    }

    private void withCredentials(HttpURLConnection connection) {
        String str = DatatypeConverter.printBase64Binary((userName + ":" + apiKey).getBytes());
        connection.addRequestProperty("Authorization", "Basic " + str);
    }

    static String capitalize(String name) {
        StringBuilder builder = new StringBuilder(name);
        builder.setCharAt(0, Character.toUpperCase(builder.charAt(0)));
        return builder.toString();
    }
}
