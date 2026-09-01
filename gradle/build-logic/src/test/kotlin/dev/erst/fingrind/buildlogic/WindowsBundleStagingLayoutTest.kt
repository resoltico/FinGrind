package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tools.jackson.databind.json.JsonMapper

class WindowsBundleStagingLayoutTest {
    private val objectMapper = JsonMapper.builder().build()

    @Test
    fun publishedWindowsX8664LayoutIsCanonicalCoherentAndDoesNotEmitAnArchive() {
        val repositoryRoot = repositoryRoot()
        val layout =
            BundleStagingLayout.plan(
                projectRootDirectory = repositoryRoot,
                version = "1.2.3",
                classifier = "windows-x86_64",
            )

        assertEquals("fingrind-1.2.3-windows-x86_64", layout.bundleName)
        assertEquals("zip", layout.archiveFormat)
        assertEquals("fingrind-1.2.3-windows-x86_64.zip", layout.archiveFileName)
        assertEquals("bin/fingrind.ps1", layout.launcherPath)
        assertEquals("src/bundle/bin/fingrind.ps1", layout.launcherTemplateSourcePath)
        assertEquals(listOf("bin/fingrind.ps1"), layout.launcherSourceIncludePaths)
        assertEquals(listOf("README.md", "quick-start-request.json"), layout.rootTemplateSourceIncludePaths)
        assertEquals("runtime/bin/java.exe", layout.runtimeJavaPath)
        assertEquals("lib/native/sqlite3.dll", layout.nativeLibraryPath)
        assertEquals("lib/native/sqlite3.dll.sha256", layout.nativeLibraryChecksumPath)
        assertEquals(
            listOf(
                "bin/fingrind.ps1",
                "lib/app/fingrind.jar",
                "lib/release-smoke/native-sqlite-format-boundary-probe.jar",
                "runtime/bin/java.exe",
                "runtime/release",
                "runtime/legal/INDEX.sha256",
                "runtime/provenance/source-jdk-release",
                "runtime/provenance/requested-modules.txt",
                "lib/native/sqlite3.dll",
                "lib/native/sqlite3.dll.sha256",
                "lib/native/toolchain-fingerprint.json",
                "lib/native/build-contract.json",
                "README.md",
                "quick-start-request.json",
                "bundle-manifest.json",
                "LICENSE",
                "LICENSE-APACHE-2.0",
                "LICENSE-CC0-1.0",
                "LICENSE-SIL-OFL-1.1",
                "LICENSE-SQLITE3MULTIPLECIPHERS",
                "LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY",
                "NOTICE",
                "NOTICE-ZULU-26.32.203",
                "PATENTS.md",
                "SOURCE_OFFER.md",
            ),
            layout.requiredArchiveFilePaths,
        )
        assertFalse(layout.launcherSourceIncludePaths.contains("bin/fingrind"))
        assertFalse(layout.requiredArchiveFilePaths.contains("runtime/bin/java"))
        assertFalse(layout.requiredArchiveFilePaths.any { it.endsWith("libsqlite3.so.0") })

        val manifest =
            objectMapper.readTree(
                BundleManifestRenderer.renderBundleManifest(
                    projectRootDirectory = repositoryRoot,
                    applicationName = "FinGrind",
                    version = "1.2.3",
                    bundleClassifier = "windows-x86_64",
                    normalizedArtifactTimestampUtc = "2026-07-28T00:00:00Z",
                ),
            )
        assertEquals(layout.archiveFormat, manifest.path("archiveFormat").requireText())
        assertEquals(
            layout.bundleTarget.classifier,
            manifest.path("bundleTarget").path("classifier").requireText(),
        )
        assertEquals(layout.launcherPath, manifest.path("launcher").requireText())
        assertEquals(
            layout.documentationFiles,
            manifest.path("documentationFiles").textValues(),
        )
        val launcherTemplatePath = repositoryRoot.resolve("cli").resolve(layout.launcherTemplateSourcePath)
        assertTrue(Files.isRegularFile(launcherTemplatePath))
        val launcherTemplate = Files.readString(launcherTemplatePath)
        assertTrue(
            launcherTemplate.contains(
                "\$runtimeJava = Join-Path \$appHome \"${layout.runtimeJavaPath}\"",
            ),
        )
        assertTrue(
            launcherTemplate.contains(
                "\$applicationJar = Join-Path \$appHome \"${layout.applicationJarPath}\"",
            ),
        )
        assertTrue(launcherTemplate.contains("-Dfingrind.runtime.distribution={{bundleRuntimeDistribution}}"))
        assertTrue(launcherTemplate.contains("-Dfingrind.runtime.bundle-target={{bundleClassifier}}"))
        assertFalse(
            renderLauncherTemplate(
                template = launcherTemplate,
                properties =
                    layout.targetTemplateProperties() +
                        layout.launcherTemplateProperties(
                            bundleRuntimeDistribution = "self-contained-bundle",
                            sqliteBundleHomeSystemProperty = "fingrind.bundle.home",
                        ),
            ).contains("{{"),
        )
    }

    @Test
    fun hostNativeAdmissionRejectsAWindowsTargetOnAnotherDeclaredHost() {
        val repositoryRoot = repositoryRoot()
        val requestedTarget =
            DistributionBundleTargetReader.bundleTarget(repositoryRoot, "windows-x86_64")
        val hostTarget =
            DistributionBundleTargetReader.bundleTarget(repositoryRoot, "linux-x86_64")

        BundleHostTargetAdmission.requireHostNative(
            requestedTarget = requestedTarget,
            hostTarget = requestedTarget,
        )
        assertFailsWith<org.gradle.api.GradleException> {
            BundleHostTargetAdmission.requireHostNative(
                requestedTarget = requestedTarget,
                hostTarget = hostTarget,
            )
        }
    }

    @Test
    fun layoutRejectsAnUnknownTargetOperatingSystemInsteadOfAssumingUnixRuntimeLayout() {
        val repositoryRoot = repositoryRoot()
        val unknownTarget =
            DistributionBundleTargetReader.bundleTarget(repositoryRoot, "windows-x86_64")
                .copy(operatingSystemId = "unknown")

        assertFailsWith<IllegalArgumentException> {
            BundleStagingLayout.plan(
                version = "1.2.3",
                bundleTarget = unknownTarget,
            )
        }
    }

    @Test
    fun layoutRejectsDriveQualifiedArchivePathsAndTraversalLikeNativeLibraryNames() {
        val repositoryRoot = repositoryRoot()
        val windowsTarget =
            DistributionBundleTargetReader.bundleTarget(repositoryRoot, "windows-x86_64")

        assertFailsWith<IllegalArgumentException> {
            BundleStagingLayout.plan(
                version = "1.2.3",
                bundleTarget = windowsTarget.copy(launcherPath = "C:/outside/fingrind.ps1"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BundleStagingLayout.plan(
                version = "1.2.3",
                bundleTarget = windowsTarget.copy(sqliteLibraryFileName = ".."),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BundleStagingLayout.plan(
                version = "1.2.3",
                bundleTarget = windowsTarget.copy(sqliteLibraryFileName = "sqlite:3.dll"),
            )
        }
    }

    @Test
    fun layoutRejectsWindowsInvalidTargetAndDerivedArchiveNamesBeforeMaterialization() {
        val repositoryRoot = repositoryRoot()
        val windowsTarget =
            DistributionBundleTargetReader.bundleTarget(repositoryRoot, "windows-x86_64")

        listOf<(BundleTargetContract) -> BundleTargetContract>(
            { target -> target.copy(launcherPath = "bin/CON.ps1") },
            { target -> target.copy(sqliteLibraryFileName = "NUL.dll") },
            { target -> target.copy(sqliteLibraryFileName = "COM¹.dll") },
            { target -> target.copy(sqliteLibraryFileName = "LPT3.dll ") },
            { target -> target.copy(launcherPath = "lib/native/SQLITE3.DLL") },
            { target -> target.copy(launcherPath = "LIB") },
            { target -> target.copy(classifier = "windows-x86_64.") },
        ).forEach { invalidTarget ->
            assertFailsWith<IllegalArgumentException> {
                BundleStagingLayout.plan(
                    version = "1.2.3",
                    bundleTarget = invalidTarget(windowsTarget),
                )
            }
        }
        assertFailsWith<IllegalArgumentException> {
            BundleStagingLayout.plan(
                version = "CON",
                bundleTarget = windowsTarget,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BundleStagingLayout.plan(
                version = "1.2.3 ",
                bundleTarget = windowsTarget,
            )
        }
    }

    @Test
    fun layoutAllowsValidUnicodeAndSpacesInPortableWindowsArchiveComponents() {
        val repositoryRoot = repositoryRoot()
        val windowsTarget =
            DistributionBundleTargetReader.bundleTarget(repositoryRoot, "windows-x86_64")

        val layout =
            BundleStagingLayout.plan(
                version = "0.62.0",
                bundleTarget =
                    windowsTarget.copy(
                        launcherPath = "bin/Rīga report.ps1",
                        sqliteLibraryFileName = "sqlite Žemaitija.dll",
                    ),
            )

        assertEquals("fingrind-0.62.0-windows-x86_64", layout.bundleName)
        assertEquals("fingrind-0.62.0-windows-x86_64.zip", layout.archiveFileName)
        assertEquals("bin/Rīga report.ps1", layout.launcherPath)
        assertEquals("lib/native/sqlite Žemaitija.dll", layout.nativeLibraryPath)
    }

    @Test
    fun manifestRenderingRejectsPathDefiningWhitespaceInsteadOfNormalizingIt() {
        val repositoryRoot = repositoryRoot()

        assertFailsWith<IllegalArgumentException> {
            BundleManifestRenderer.renderBundleManifest(
                projectRootDirectory = repositoryRoot,
                applicationName = "FinGrind",
                version = "1.2.3 ",
                bundleClassifier = "windows-x86_64",
                normalizedArtifactTimestampUtc = "2026-07-28T00:00:00Z",
            )
        }
        assertFailsWith<IllegalStateException> {
            BundleManifestRenderer.renderBundleManifest(
                projectRootDirectory = repositoryRoot,
                applicationName = "FinGrind",
                version = "1.2.3",
                bundleClassifier = "windows-x86_64 ",
                normalizedArtifactTimestampUtc = "2026-07-28T00:00:00Z",
            )
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

    private fun renderLauncherTemplate(
        template: String,
        properties: Map<String, String>,
    ): String =
        properties.entries.fold(template) { rendered, (name, value) ->
            rendered.replace("{{$name}}", value)
        }
}
