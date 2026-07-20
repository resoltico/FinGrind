package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.kotlin.dsl.register
import org.junit.jupiter.api.io.TempDir

class VerificationPolicySupportTest {
    @TempDir lateinit var temporaryDirectory: Path

    @Test
    fun cryptographicPrimitiveSeam_acceptsTheDedicatedCoreOwner() {
        val projectDirectory = temporaryDirectory.resolve("core")
        writeSource(
            projectDirectory,
            "dev/erst/fingrind/core/CryptographicPrimitives.java",
            """
            package dev.erst.fingrind.core;
            import java.security.MessageDigest;
            final class CryptographicPrimitives {}
            """.trimIndent(),
        )

        sourcePolicyTask(projectDirectory, ":core").verify()
    }

    @Test
    fun cryptographicPrimitiveSeam_rejectsOtherProductionOwners() {
        val projectDirectory = temporaryDirectory.resolve("sqlite")
        writeSource(
            projectDirectory,
            "dev/erst/fingrind/sqlite/UnexpectedCryptoOwner.java",
            """
            package dev.erst.fingrind.sqlite;
            import java.security.interfaces.RSAPrivateCrtKey;
            final class UnexpectedCryptoOwner {}
            """.trimIndent(),
        )

        val exception =
            assertFailsWith<GradleException> {
                sourcePolicyTask(projectDirectory, ":sqlite").verify()
            }

        kotlin.test.assertTrue(
            exception.message.orEmpty().contains("cryptographic primitives are owned only by the explicit crypto seam"),
        )
    }

    private fun sourcePolicyTask(
        projectDirectory: Path,
        projectPath: String,
    ): VerifyJavaSourcePoliciesTask {
        Files.createDirectories(projectDirectory)
        val project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build()
        return project.tasks
            .register<VerifyJavaSourcePoliciesTask>("verifyJavaSourcePolicies")
            .get()
            .apply {
                projectPathValue.set(projectPath)
                projectDirectoryPath.set(projectDirectory.toFile().invariantSeparatorsPath())
                sourceFiles.from(
                    project.fileTree(projectDirectory.toFile()) {
                        include("src/main/java/**/*.java")
                    },
                )
            }
    }

    private fun writeSource(projectDirectory: Path, relativePath: String, source: String) {
        val sourcePath = projectDirectory.resolve("src/main/java").resolve(relativePath)
        Files.createDirectories(sourcePath.parent)
        Files.writeString(sourcePath, source)
    }
}
