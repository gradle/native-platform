import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.Callable

/**
 * Uploads each publication of the project to bintray.
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
        }
        val upload = project.tasks.create("upload", UploadTask::class.java) { task ->
            task.repoDir = repoDir
            task.publications = object : Callable<Collection<MavenPublication>> {
                override fun call(): Collection<MavenPublication> {
                    val extension = project.extensions.getByType(PublishingExtension::class.java)
                    return extension.publications.withType(MavenPublication::class.java)
                }
            }
        }
        project.gradle.taskGraph.whenReady { graph ->
            if (graph.hasTask(upload)) {
                if (!project.hasProperty("bintrayUserName")) {
                    throw IllegalStateException("Project property 'bintrayUserName' not provided.")
                }
                upload.userName = project.property("bintrayUserName").toString()
                if (!project.hasProperty("bintrayApiKey")) {
                    throw IllegalStateException("Project property 'bintrayApiKey' not provided.")
                }
                upload.apiKey = project.property("bintrayApiKey").toString()
            }
        }
    }

    open class UploadTask : DefaultTask() {
        lateinit var publications: Callable<Collection<MavenPublication>>
        lateinit var repoDir: Callable<File>
        lateinit var userName: String
        lateinit var apiKey: String

        init {
            val dependencies = object : Callable<Collection<Task>> {
                override fun call(): Collection<Task> {
                    val tasks: List<Task> = publications.call().map { publication ->
                        project.tasks.getByName("publish${publication.name.capitalize()}PublicationToMavenRepository")
                    }
                    return tasks
                }
            }
            dependsOn(dependencies)
        }

        @TaskAction
        fun upload() {
            publications.call().forEach { publication ->
                println("Uploading publication ${publication.name}")
                publication.artifacts.forEach { artifact ->
                    upload(publication.groupId, publication.artifactId, publication.version, artifact.classifier, artifact.extension)
                }
                upload(publication.groupId, publication.artifactId, publication.version, null, "pom")
            }
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
            val uploadUrl = URL("https://api.bintray.com/maven/adammurdoch/maven/${groupId}:${artifactId}/${mavenPath}")
            val connection = uploadUrl.openConnection() as HttpURLConnection
            file.inputStream().use { instr ->
                withCredentials(connection)
                connection.addRequestProperty("Content-Length", file.length().toString())
                connection.requestMethod = "PUT"
                connection.doOutput = true
                instr.copyTo(connection.outputStream)
                val collect = ByteArrayOutputStream()
                connection.inputStream.copyTo(collect)
                println(collect.toString())
            }
        }
    }
}