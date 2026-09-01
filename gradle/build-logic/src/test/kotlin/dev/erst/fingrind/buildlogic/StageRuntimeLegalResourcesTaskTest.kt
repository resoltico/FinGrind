package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.testfixtures.ProjectBuilder

class StageRuntimeLegalResourcesTaskTest {
    @Test
    fun stagesRootAndMetaInfLegalResourcesAgainstTheReviewedRawByteLock() {
        withFixture { root ->
            val artifact =
                writeJar(
                    root.resolve("dependencies/example-1.0.jar"),
                    mapOf(
                        "LICENSE" to "root license",
                        "META-INF/NOTICE.txt" to "dependency notice",
                        "com/example/Example.class" to "class bytes",
                    ),
                )
            val lock = writeLock(root.resolve("legal.lock.tsv"), artifact)
            val output = root.resolve("output")

            task(root, artifact, lock, output).stage()

            assertEquals(
                "root license",
                Files.readString(output.resolve("example-1.0/LICENSE")),
            )
            assertEquals(
                "dependency notice",
                Files.readString(output.resolve("example-1.0/NOTICE.txt")),
            )
            assertEquals(Files.readString(lock), Files.readString(output.resolve("INDEX.tsv")))
        }
    }

    @Test
    fun rejectsAnExternalRuntimeJarWithoutAReviewedLegalResource() {
        withFixture { root ->
            val artifact =
                writeJar(
                    root.resolve("dependencies/unclassified-1.0.jar"),
                    mapOf("com/example/Example.class" to "class bytes"),
                )
            val lock =
                root.resolve("legal.lock.tsv").also { path ->
                    Files.writeString(
                        path,
                        "artifact\tartifact-sha256\tresource\tresource-sha256\n",
                    )
                }

            val failure =
                assertFailsWith<IllegalStateException> {
                    task(root, artifact, lock, root.resolve("output")).stage()
                }
            assertTrue(failure.message.orEmpty().contains("did not carry a reviewed legal resource"))
        }
    }

    @Test
    fun rejectsRawArtifactOrLegalResourceDriftFromTheLock() {
        withFixture { root ->
            val artifact =
                writeJar(
                    root.resolve("dependencies/example-1.0.jar"),
                    mapOf("META-INF/LICENSE" to "changed license"),
                )
            val lock =
                root.resolve("legal.lock.tsv").also { path ->
                    Files.writeString(
                        path,
                        "artifact\tartifact-sha256\tresource\tresource-sha256\n" +
                            "example-1.0.jar\t0000000000000000000000000000000000000000000000000000000000000000\tMETA-INF/LICENSE\t0000000000000000000000000000000000000000000000000000000000000000\n",
                    )
                }

            val failure =
                assertFailsWith<IllegalStateException> {
                    task(root, artifact, lock, root.resolve("output")).stage()
                }
            assertTrue(failure.message.orEmpty().contains("differ from"))
        }
    }

    @Test
    fun rejectsUnsafeOrCollidingStagedLegalPaths() {
        withFixture { root ->
            val unsafeArtifact =
                writeJar(
                    root.resolve("unsafe/unsafe-1.0.jar"),
                    mapOf("META-INF/../../escape-LICENSE" to "unsafe"),
                )
            val unsafeLock = writeLock(root.resolve("unsafe.lock.tsv"), unsafeArtifact)
            assertFailsWith<IllegalArgumentException> {
                task(root, unsafeArtifact, unsafeLock, root.resolve("unsafe-output")).stage()
            }

            val collidingArtifact =
                writeJar(
                    root.resolve("collision/collision-1.0.jar"),
                    mapOf(
                        "LICENSE" to "root",
                        "META-INF/LICENSE" to "meta",
                    ),
                )
            val collidingLock = writeLock(root.resolve("collision.lock.tsv"), collidingArtifact)
            assertFailsWith<IllegalStateException> {
                task(root, collidingArtifact, collidingLock, root.resolve("collision-output")).stage()
            }
        }
    }

    private fun task(
        projectDirectory: Path,
        artifact: Path,
        lock: Path,
        output: Path,
    ): StageRuntimeLegalResourcesTask {
        val project =
            ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build()
        return project.tasks.create(
            "stageRuntimeLegalResourcesFixture",
            StageRuntimeLegalResourcesTask::class.java,
        ).apply {
            runtimeArtifacts.from(artifact.toFile())
            legalResourceLockFile.set(lock.toFile())
            outputDirectory.set(output.toFile())
        }
    }

    private fun writeJar(path: Path, entries: Map<String, String>): Path {
        path.parent.createDirectories()
        ZipOutputStream(Files.newOutputStream(path)).use { archive ->
            entries.forEach { (name, contents) ->
                archive.putNextEntry(ZipEntry(name))
                archive.write(contents.toByteArray())
                archive.closeEntry()
            }
        }
        return path
    }

    private fun writeLock(path: Path, artifact: Path): Path {
        val rows =
            java.util.zip.ZipFile(artifact.toFile()).use { archive ->
                archive.entries()
                    .asSequence()
                    .filterNot { entry -> entry.isDirectory }
                    .filter { entry ->
                        val fileName = entry.name.substringAfterLast('/').uppercase()
                        fileName.contains("LICENSE") ||
                            fileName.contains("NOTICE") ||
                            fileName.startsWith("COPYING") ||
                            fileName.startsWith("COPYRIGHT") ||
                            fileName == "UNLICENSE" ||
                            fileName.startsWith("THIRD-PARTY") ||
                            fileName.startsWith("THIRDPARTY") ||
                            fileName.startsWith("LEGAL")
                    }
                    .sortedBy { entry -> entry.name }
                    .map { entry ->
                        val resourceBytes = archive.getInputStream(entry).use { it.readAllBytes() }
                        listOf(
                                artifact.fileName.toString(),
                                sha256(Files.readAllBytes(artifact)),
                                entry.name,
                                sha256(resourceBytes),
                            )
                            .joinToString("\t")
                    }
                    .toList()
            }
        Files.writeString(
            path,
            buildString {
                append("artifact\tartifact-sha256\tresource\tresource-sha256\n")
                rows.forEach { row -> append(row).append('\n') }
            },
        )
        return path
    }

    private fun sha256(bytes: ByteArray): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun withFixture(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("runtime-legal-resources-test")
        try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
