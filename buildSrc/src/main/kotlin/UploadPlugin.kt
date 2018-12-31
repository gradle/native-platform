import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.Callable

/**
 * Uploads each publication of the project to bintray. Uses the bintray API directly rather than the bintray plugin, as the plugin does not
 * handle multiple packages per project.
 */
class UploadPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("maven-publish")

        val repoDir = object : Callable<File> {
            override fun call(): File {
                return project.rootProject.layout.buildDirectory.dir("repo").get().asFile
            }
        }
        project.extensions.configure(PublishingExtension::class.java) { extension ->
            extension.repositories.maven { repo ->
                repo.setUrl(repoDir.call())
            }
            extension.publications.withType(MavenPublication::class.java) { publication ->
                val upload = project.tasks.create("upload${publication.name.capitalize()}", UploadTask::class.java) { task ->
                    task.group = "Upload"
                    task.description = "Upload publication ${publication.name}"
                    task.repoDir = repoDir
                    task.publication = publication
                }
                if (publication.name != "main") {
                    val lifecycleTask = project.tasks.maybeCreate("uploadJni")
                    lifecycleTask.group = "Upload"
                    lifecycleTask.description = "Upload all JNI publications"
                    lifecycleTask.dependsOn(upload)
                }
            }
        }
        project.gradle.taskGraph.whenReady { graph ->
            graph.allTasks.forEach { task ->
                if (task is UploadTask) {
                    if (!project.hasProperty("bintrayUserName")) {
                        throw IllegalStateException("Project property 'bintrayUserName' not provided.")
                    }
                    task.userName = project.property("bintrayUserName").toString()
                    if (!project.hasProperty("bintrayApiKey")) {
                        throw IllegalStateException("Project property 'bintrayApiKey' not provided.")
                    }
                    task.apiKey = project.property("bintrayApiKey").toString()
                }
            }
        }
    }

    open class UploadTask : DefaultTask() {
        lateinit var publication: MavenPublication
        lateinit var repoDir: Callable<File>
        lateinit var userName: String
        lateinit var apiKey: String

        init {
            val dependencies = object : Callable<Task> {
                override fun call(): Task {
                    return project.tasks.getByName("publish${publication.name.capitalize()}PublicationToMavenRepository")
                }
            }
            dependsOn(dependencies)
        }

        @TaskAction
        fun upload() {
            println("Uploading publication ${publication.name}")
            publication.artifacts.forEach { artifact ->
                                upload(publication.groupId, publication.artifactId, publication.version, artifact.classifier, artifact.extension)
            }
            upload(publication.groupId, publication.artifactId, publication.version, null, "pom")
        }

        private fun upload(groupId: String, artifactId: String, version: String, classifier: String?, extension: String) {
            val baseName = artifactId + "-" + version + if (classifier.isNullOrBlank()) {
                ""
            } else {
                "-" + classifier
            } + "." + extension
            upload(groupId, artifactId, version, baseName)
            upload(groupId, artifactId, version, "${baseName}.sha1")
            upload(groupId, artifactId, version, "${baseName}.md5")
        }

        fun withCredentials(connection: HttpURLConnection) {
            val str = Base64.getEncoder().encodeToString("${userName}:${apiKey}".toByteArray())
            connection.addRequestProperty("Authorization", "Basic ${str}")
        }

        private fun upload(groupId: String, artifactId: String, version: String, baseName: String) {
            println("Uploading file ${baseName}")
            val mavenPath = groupId.replace(".", "/") + "/" + artifactId + "/" + version + "/" + baseName
            val file = File(repoDir.call(), mavenPath)
            if (!file.isFile) {
                throw IllegalArgumentException("Artifact ${baseName} file ${file} does not exist.")
            }
            file.inputStream().use { instr ->
                val uploadUrl = uploadUrl(groupId, artifactId, mavenPath)
                upload(uploadUrl, file.length(), instr)
            }
        }

        private fun upload(uploadUrl: URL, length: Long, instr: InputStream): Long {
            val connection = uploadUrl.openConnection() as HttpURLConnection
            withCredentials(connection)
            connection.addRequestProperty("Content-Length", length.toString())
            connection.requestMethod = "PUT"
            connection.doOutput = true
            instr.copyTo(connection.outputStream)
            val collect = ByteArrayOutputStream()
            return connection.inputStream.copyTo(collect)
        }

        private fun uploadUrl(groupId: String, artifactId: String, mavenPath: String) = URL("https://api.bintray.com/maven/adammurdoch/maven/${groupId}:${artifactId}/${mavenPath}")
    }
}
