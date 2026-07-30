package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tools.jackson.databind.json.JsonMapper

class SyntheticTargetBundleLayoutTest {
    private val objectMapper = JsonMapper.builder().build()

    @Test
    fun materializesThePublishedWindowsLayoutWithoutCreatingAnArchive() {
        val repositoryRoot = repositoryRoot()
        val temporaryRoot = Files.createTempDirectory("synthetic-target-bundle-layout")
        try {
            val normalizedTimestamp = "2026-07-28T00:00:00Z"
            val bundleRoot =
                SyntheticTargetBundleLayout.materialize(
                    repositoryRootDirectory = repositoryRoot,
                    bundleSourceDirectory = repositoryRoot.resolve("cli/src/bundle"),
                    destinationDirectory = temporaryRoot.resolve("synthetic-target-layout"),
                    applicationName = "FinGrind",
                    version = "1.2.3",
                    bundleClassifier = "windows-x86_64",
                    normalizedArtifactTimestampUtc = normalizedTimestamp,
                )
            val layout =
                BundleStagingLayout.plan(
                    projectRootDirectory = repositoryRoot,
                    version = "1.2.3",
                    classifier = "windows-x86_64",
                )

            assertTrue(layout.requiredArchiveFilePaths.all { Files.isRegularFile(bundleRoot.resolve(it)) })
            assertFalse(Files.exists(bundleRoot.resolve(layout.archiveFileName)))
            Files.walk(bundleRoot).use { paths ->
                assertFalse(paths.anyMatch { it.fileName.toString().endsWith(".zip") })
            }

            val manifest = objectMapper.readTree(Files.readString(bundleRoot.resolve("bundle-manifest.json")))
            assertEquals("windows-x86_64", manifest.path("bundleTarget").path("classifier").requireText())
            assertEquals("zip", manifest.path("archiveFormat").requireText())
            assertEquals(layout.launcherPath, manifest.path("launcher").requireText())

            val launcher = Files.readString(bundleRoot.resolve(layout.launcherPath))
            assertFalse(launcher.contains("{{"))
            assertTrue(
                launcher.contains(
                    "--enable-native-access=dev.erst.fingrind.cli",
                ),
            )
            assertTrue(launcher.contains("runtime/bin/java.exe"))

            JarFile(bundleRoot.resolve(layout.applicationJarPath).toFile()).use { applicationJar ->
                val manifestAttributes = applicationJar.manifest.mainAttributes
                assertEquals(
                    "dev.erst.fingrind.cli",
                    manifestAttributes.getValue("Automatic-Module-Name"),
                )
                assertEquals("dev.erst.fingrind.cli.App", manifestAttributes.getValue("Main-Class"))
            }
            assertTrue(
                Files.readString(bundleRoot.resolve(layout.runtimeJavaPath)).startsWith("synthetic target runtime"),
            )
            assertTrue(
                Files.readString(bundleRoot.resolve(layout.nativeLibraryPath)).startsWith("synthetic target native-library"),
            )
            assertEquals(
                HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(bundleRoot.resolve(layout.nativeLibraryPath))),
                ) + "  ${layout.nativeLibraryFileName}",
                Files.readString(bundleRoot.resolve(layout.nativeLibraryChecksumPath)).trim(),
            )
            val expectedEpochSecond = Instant.parse(normalizedTimestamp).epochSecond
            Files.walk(bundleRoot).use { paths ->
                paths.filter { Files.isRegularFile(it) }.forEach { path ->
                    assertEquals(expectedEpochSecond, Files.getLastModifiedTime(path).toInstant().epochSecond)
                }
            }
        } finally {
            DistributionContractReaderTestSupport.deleteTree(temporaryRoot)
        }
    }

    private fun repositoryRoot(): Path =
        generateSequence(Path.of("").toAbsolutePath().normalize()) { candidate -> candidate.parent }
            .firstOrNull { candidate ->
                Files.isRegularFile(
                    candidate.resolve(
                        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/bundle-layout-contract.json",
                    ),
                ) &&
                    Files.isRegularFile(candidate.resolve("cli/src/bundle/bin/fingrind.ps1"))
            }
            ?: error("Could not locate the FinGrind repository root from the build-logic test process.")
}
