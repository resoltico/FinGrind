package dev.erst.fingrind.buildlogic

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class PrepareJacocoSnapshotArtifactsTask : DefaultTask() {
    companion object {
        private const val JACOCO_SNAPSHOT_FETCH_USER_AGENT = "FinGrind-JaCoCo-Snapshot-Verifier/1.0"
        private const val DOWNLOAD_CONNECT_TIMEOUT_MILLIS = 15_000
        private const val DOWNLOAD_READ_TIMEOUT_MILLIS = 30_000
        private const val DOWNLOAD_MAX_ATTEMPTS = 6
        private const val DOWNLOAD_RETRY_DELAY_MILLIS = 2_000L
    }

    @get:Input
    abstract val snapshotBaseVersion: Property<String>

    @get:Input
    abstract val resolvedVersion: Property<String>

    @get:OutputFile
    abstract val agentJarFile: RegularFileProperty

    @get:OutputFile
    abstract val antJarFile: RegularFileProperty

    @get:OutputFile
    abstract val coreJarFile: RegularFileProperty

    @get:OutputFile
    abstract val reportJarFile: RegularFileProperty

    @TaskAction
    fun prepare() {
        download(
            moduleId = "org.jacoco.agent",
            outputFile = agentJarFile.get().asFile.toPath(),
        )
        download(
            moduleId = "org.jacoco.ant",
            outputFile = antJarFile.get().asFile.toPath(),
        )
        download(
            moduleId = "org.jacoco.core",
            outputFile = coreJarFile.get().asFile.toPath(),
        )
        download(
            moduleId = "org.jacoco.report",
            outputFile = reportJarFile.get().asFile.toPath(),
        )
    }

    private fun download(
        moduleId: String,
        outputFile: java.nio.file.Path,
    ) {
        val versionDirectory = snapshotBaseVersion.get()
        val versionValue = resolvedVersion.get()
        val fileName = "$moduleId-$versionValue.jar"
        val artifactUri =
            URI(
                "https://central.sonatype.com/repository/maven-snapshots/" +
                    "org/jacoco/$moduleId/$versionDirectory/$fileName",
            )
        Files.createDirectories(outputFile.parent)
        val tempFile = Files.createTempFile(outputFile.parent, outputFile.fileName.toString(), ".part")
        try {
            for (attempt in 1..DOWNLOAD_MAX_ATTEMPTS) {
                try {
                    downloadOnce(artifactUri, tempFile)
                    Files.move(
                        tempFile,
                        outputFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                    return
                } catch (exception: IOException) {
                    Files.deleteIfExists(tempFile)
                    if (attempt == DOWNLOAD_MAX_ATTEMPTS) {
                        throw exception
                    }
                    pauseBeforeRetry(artifactUri, attempt, exception)
                }
            }
            error("unreachable")
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    private fun downloadOnce(
        artifactUri: URI,
        tempFile: java.nio.file.Path,
    ) {
        val connection = artifactUri.toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = DOWNLOAD_READ_TIMEOUT_MILLIS
            connection.setRequestProperty("User-Agent", JACOCO_SNAPSHOT_FETCH_USER_AGENT)
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Server returned HTTP response code: $responseCode for URL: $artifactUri")
            }
            connection.inputStream.use { inputStream ->
                Files.newOutputStream(tempFile).use(inputStream::copyTo)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun pauseBeforeRetry(
        artifactUri: URI,
        attempt: Int,
        exception: IOException,
    ) {
        try {
            Thread.sleep(DOWNLOAD_RETRY_DELAY_MILLIS * attempt)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while retrying JaCoCo snapshot download from $artifactUri", exception)
        }
    }
}
