package dev.erst.fingrind.buildlogic

import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class VerifyManagedSqliteSourceTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:Input
    abstract val expectedSourcePackageId: Property<String>

    @get:Input
    abstract val expectedFileDigests: MapProperty<String, String>

    @TaskAction
    fun verify() {
        val vendoredReleaseDirectory = sourceDirectory.get().asFile
        if (!vendoredReleaseDirectory.isDirectory) {
            throw GradleException(
                "Missing vendored SQLite release directory at ${vendoredReleaseDirectory.absolutePath}",
            )
        }
        val expectedPackageId = expectedSourcePackageId.get()
        if (vendoredReleaseDirectory.name != expectedPackageId) {
            throw GradleException(
                "Vendored SQLite release directory ${vendoredReleaseDirectory.name} does not match the pinned package id $expectedPackageId.",
            )
        }
        val expectedDigests = expectedFileDigests.get()
        val actualRelativeFiles =
            vendoredReleaseDirectory
                .walkTopDown()
                .filter { it.isFile }
                .map { file -> file.relativeTo(vendoredReleaseDirectory).invariantSeparatorsPath }
                .toSortedSet()
        val expectedRelativeFiles = expectedDigests.keys.toSortedSet()
        val missingFiles = expectedRelativeFiles - actualRelativeFiles
        val unexpectedFiles = actualRelativeFiles - expectedRelativeFiles
        if (missingFiles.isNotEmpty() || unexpectedFiles.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("Vendored SQLite release manifest drift detected for $expectedPackageId.")
                    if (missingFiles.isNotEmpty()) {
                        append(" Missing files: ")
                        append(missingFiles.joinToString())
                        append('.')
                    }
                    if (unexpectedFiles.isNotEmpty()) {
                        append(" Unexpected files: ")
                        append(unexpectedFiles.joinToString())
                        append('.')
                    }
                },
            )
        }
        expectedDigests.forEach { (relativePath, expectedDigest) ->
            val vendoredFile = vendoredReleaseDirectory.resolve(relativePath)
            val actualDigest =
                MessageDigest.getInstance("SHA3-256")
                    .digest(vendoredFile.readBytes().normalizeLineEndings())
                    .joinToString(separator = "") { byte -> "%02x".format(byte) }
            if (actualDigest != expectedDigest) {
                throw GradleException(
                    "Vendored SQLite file hash mismatch. Expected $expectedDigest but found $actualDigest for ${vendoredFile.absolutePath}.",
                )
            }
        }
    }

    private fun ByteArray.normalizeLineEndings(): ByteArray {
        var index = 0
        var sawCarriageReturn = false
        val normalized = ByteArray(size)
        for (byte in this) {
            if (byte == '\r'.code.toByte()) {
                normalized[index++] = '\n'.code.toByte()
                sawCarriageReturn = true
                continue
            }
            if (sawCarriageReturn && byte == '\n'.code.toByte()) {
                sawCarriageReturn = false
                continue
            }
            sawCarriageReturn = false
            normalized[index++] = byte
        }
        return normalized.copyOf(index)
    }
}
