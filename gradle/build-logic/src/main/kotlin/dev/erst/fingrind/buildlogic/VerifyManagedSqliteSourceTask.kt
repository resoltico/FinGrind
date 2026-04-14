package dev.erst.fingrind.buildlogic

import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class VerifyManagedSqliteSourceTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFile: RegularFileProperty

    @get:Input
    abstract val expectedSha3: Property<String>

    @TaskAction
    fun verify() {
        val sqliteSource = sourceFile.get().asFile
        if (!sqliteSource.isFile) {
            throw GradleException("Missing vendored SQLite source at ${sqliteSource.absolutePath}")
        }
        // Normalize to LF before hashing so Git checkout line-ending policy cannot falsify the
        // integrity check across CI and developer workstations.
        val actualSourceSha3 =
            MessageDigest.getInstance("SHA3-256")
                .digest(sqliteSource.readBytes().normalizeLineEndings())
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
        if (actualSourceSha3 != expectedSha3.get()) {
            throw GradleException(
                "Vendored SQLite source hash mismatch. Expected ${expectedSha3.get()} but found $actualSourceSha3 for ${sqliteSource.absolutePath}.",
            )
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
