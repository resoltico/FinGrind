package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.kotlin.dsl.register
import org.junit.jupiter.api.io.TempDir

class VerificationPolicySupportTest {
    private companion object {
        const val FOREIGN_MEMORY_OWNERSHIP_DESCRIPTION =
            "Java FFM usage is owned only by the SQLite bridge module, the Windows protected-output seam, " +
                "and the private-output directory-durability seam"
        const val NATIVE_LIBRARY_LOADING_OWNERSHIP_DESCRIPTION =
            "Native library loading is owned only by the SQLite bridge module"
    }

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

    @Test
    fun cryptographicPrimitiveSeam_rejectsPrivateKeyCarriersOutsideSeam() {
        val projectDirectory = temporaryDirectory.resolve("contract")
        writeSource(
            projectDirectory,
            "dev/erst/fingrind/contract/UnexpectedPrivateKeyCarrier.java",
            """
            package dev.erst.fingrind.contract;
            import java.security.KeyPair;
            import java.security.spec.PKCS8EncodedKeySpec;
            final class UnexpectedPrivateKeyCarrier {}
            """.trimIndent(),
        )

        val exception =
            assertFailsWith<GradleException> {
                sourcePolicyTask(projectDirectory, ":contract").verify()
            }

        assertEquals(
            2,
            Regex("cryptographic primitives are owned only by the explicit crypto seam")
                .findAll(exception.message.orEmpty())
                .count(),
        )
    }

    @Test
    fun foreignMemorySeam_acceptsExplicitCoreFfmOwnersOutsideSqlite() {
        val projectDirectory = temporaryDirectory.resolve("core")
        writeSource(
            projectDirectory,
            "dev/erst/fingrind/core/PrivateOutputDirectoryFfmTransport.java",
            """
            package dev.erst.fingrind.core;
            import java.lang.foreign.Arena;
            final class PrivateOutputDirectoryFfmTransport {
              void force() {
                try {
                } catch (Throwable ignored) {
                }
              }
            }
            """.trimIndent(),
        )

        sourcePolicyTask(projectDirectory, ":core").verify()

        writeSource(
            projectDirectory,
            "dev/erst/fingrind/core/PrivateOutputDirectoryPlatformSpec.java",
            """
            package dev.erst.fingrind.core;
            import java.lang.foreign.Arena;
            enum PrivateOutputDirectoryPlatformSpec { WINDOWS }
            """.trimIndent(),
        )

        sourcePolicyTask(projectDirectory, ":core").verify()

        writeSource(
            projectDirectory,
            "dev/erst/fingrind/core/WindowsPrivateOutputFileFfmInvocation.java",
            """
            package dev.erst.fingrind.core;
            import java.lang.foreign.Arena;
            final class WindowsPrivateOutputFileFfmInvocation {
              void invoke() {
                try {
                } catch (Throwable ignored) {
                }
              }
            }
            """.trimIndent(),
        )

        sourcePolicyTask(projectDirectory, ":core").verify()

        writeSource(
            projectDirectory,
            "dev/erst/fingrind/core/WindowsPrivateOutputFileAccountSidResolver.java",
            """
            package dev.erst.fingrind.core;
            import java.lang.foreign.Arena;
            final class WindowsPrivateOutputFileAccountSidResolver {}
            """.trimIndent(),
        )

        sourcePolicyTask(projectDirectory, ":core").verify()

        writeSource(
            projectDirectory,
            "dev/erst/fingrind/core/WindowsPrivateOutputFileHandleLocks.java",
            """
            package dev.erst.fingrind.core;
            import java.lang.foreign.Arena;
            final class WindowsPrivateOutputFileHandleLocks {}
            """.trimIndent(),
        )

        sourcePolicyTask(projectDirectory, ":core").verify()

        writeSource(
            projectDirectory,
            "dev/erst/fingrind/core/WindowsPrivateOutputFileOperationArena.java",
            """
            package dev.erst.fingrind.core;
            import java.lang.foreign.Arena;
            final class WindowsPrivateOutputFileOperationArena {}
            """.trimIndent(),
        )

        sourcePolicyTask(projectDirectory, ":core").verify()
    }

    @Test
    fun throwableInvocationSeam_acceptsTheExactWindowsProtectedOutputFfmBoundary() {
        val projectDirectory = temporaryDirectory.resolve("core")
        writeSource(
            projectDirectory,
            "dev/erst/fingrind/core/WindowsPrivateOutputFileFfmInvocation.java",
            """
            package dev.erst.fingrind.core;
            final class WindowsPrivateOutputFileFfmInvocation {
              void invoke() {
                try {
                } catch (Throwable ignored) {
                }
              }
            }
            """.trimIndent(),
        )

        sourcePolicyTask(projectDirectory, ":core").verify()
    }

    @Test
    fun foreignMemorySeam_rejectsOtherProductionOwners() {
        val projectDirectory = temporaryDirectory.resolve("core")
        writeSource(
            projectDirectory,
            "dev/erst/fingrind/core/UnexpectedNativeOwner.java",
            """
            package dev.erst.fingrind.core;
            import java.lang.foreign.Arena;
            final class UnexpectedNativeOwner {}
            """.trimIndent(),
        )

        val exception =
            assertFailsWith<GradleException> {
                sourcePolicyTask(projectDirectory, ":core").verify()
            }

        kotlin.test.assertTrue(
            exception.message.orEmpty().contains(FOREIGN_MEMORY_OWNERSHIP_DESCRIPTION),
        )
    }

    @Test
    fun foreignMemorySeam_acceptsEveryExactWindowsProtectedOutputTestOwner() {
        val projectDirectory = temporaryDirectory.resolve("core")
        setOf(
            "WindowsPrivateOutputFileBindingContractTest",
            "WindowsPrivateOutputFileCallTestSupport",
            "WindowsPrivateOutputFileCloseFailingArena",
            "WindowsCurrentTokenAclPrincipalMatcherTest",
            "WindowsPrivateOutputFileFfmCallsTest",
            "WindowsPrivateOutputFileFfmTransportTest",
            "WindowsPrivateOutputFileFfmTransportResourceLifecycleTest",
            "WindowsPrivateOutputFileHandleTest",
            "WindowsPrivateOutputFileLockArenaOwnershipTest",
            "WindowsPrivateOutputFileNativeTest",
            "WindowsTrustedAclPrincipalMatcherTest",
        ).forEach { className ->
            writeTestSource(
                projectDirectory,
                "dev/erst/fingrind/core/$className.java",
                """
                package dev.erst.fingrind.core;
                import java.lang.foreign.Arena;
                final class $className {}
                """.trimIndent(),
            )
        }

        sourcePolicyTask(projectDirectory, ":core").verify()
    }

    @Test
    fun foreignMemorySeam_rejectsNonInventoryWindowsTestOwners() {
        val projectDirectory = temporaryDirectory.resolve("core")
        writeTestSource(
            projectDirectory,
            "dev/erst/fingrind/core/WindowsPrivateOutputFileTransportTest.java",
            """
            package dev.erst.fingrind.core;
            import java.lang.foreign.Arena;
            final class WindowsPrivateOutputFileTransportTest {}
            """.trimIndent(),
        )

        val exception =
            assertFailsWith<GradleException> {
                sourcePolicyTask(projectDirectory, ":core").verify()
            }

        kotlin.test.assertTrue(
            exception.message.orEmpty().contains(FOREIGN_MEMORY_OWNERSHIP_DESCRIPTION),
        )
    }

    @Test
    fun nativeLibraryLoadingSeam_rejectsWindowsFfmOnlyTestOwners() {
        val projectDirectory = temporaryDirectory.resolve("core")
        writeTestSource(
            projectDirectory,
            "dev/erst/fingrind/core/WindowsPrivateOutputFileBindingContractTest.java",
            """
            package dev.erst.fingrind.core;
            import java.lang.foreign.Arena;
            final class WindowsPrivateOutputFileBindingContractTest {
              void load() {
                System.loadLibrary("kernel32");
              }
            }
            """.trimIndent(),
        )

        val exception =
            assertFailsWith<GradleException> {
                sourcePolicyTask(projectDirectory, ":core").verify()
            }

        kotlin.test.assertTrue(
            exception.message.orEmpty().contains(NATIVE_LIBRARY_LOADING_OWNERSHIP_DESCRIPTION),
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
                        include("src/test/java/**/*.java")
                    },
                )
            }
    }

    private fun writeSource(projectDirectory: Path, relativePath: String, source: String) {
        val sourcePath = projectDirectory.resolve("src/main/java").resolve(relativePath)
        Files.createDirectories(sourcePath.parent)
        Files.writeString(sourcePath, source)
    }

    private fun writeTestSource(projectDirectory: Path, relativePath: String, source: String) {
        val sourcePath = projectDirectory.resolve("src/test/java").resolve(relativePath)
        Files.createDirectories(sourcePath.parent)
        Files.writeString(sourcePath, source)
    }
}
