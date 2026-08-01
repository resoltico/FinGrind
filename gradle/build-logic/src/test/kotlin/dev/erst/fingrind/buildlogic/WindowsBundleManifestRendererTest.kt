package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tools.jackson.databind.json.JsonMapper

class WindowsBundleManifestRendererTest {
    private val objectMapper = JsonMapper.builder().build()

    @Test
    fun renderBundleManifest_rendersThePublishedWindowsX8664ContractWithoutBuildingIt() {
        val repositoryRoot = Files.createTempDirectory("windows-bundle-manifest-renderer")
        try {
            writeManifestContracts(repositoryRoot)

            val rendered =
                BundleManifestRenderer.renderBundleManifest(
                    projectRootDirectory = repositoryRoot,
                    applicationName = "FinGrind",
                    version = "1.2.3",
                    bundleClassifier = "windows-x86_64",
                    normalizedArtifactTimestampUtc = "2026-07-28T00:00:00Z",
                )

            val manifest = objectMapper.readTree(rendered)
            assertEquals("FinGrind", manifest.path("application").requireText())
            assertEquals("1.2.3", manifest.path("version").requireText())
            assertEquals(
                "2026-07-28T00:00:00Z",
                manifest.path("normalizedArtifactTimestampUtc").requireText(),
            )
            assertEquals("self-contained-cli-bundle", manifest.path("artifactType").requireText())
            assertEquals("zip", manifest.path("archiveFormat").requireText())
            assertEquals(
                "self-contained-bundle",
                manifest.path("runtimeDistribution").requireText(),
            )
            assertEquals(
                "self-contained-bundle",
                manifest.path("publicCliDistribution").requireText(),
            )
            assertEquals(
                "windows-x86_64",
                manifest.path("bundleTarget").path("classifier").requireText(),
            )
            assertEquals(
                "windows",
                manifest.path("bundleTarget").path("operatingSystem").requireText(),
            )
            assertEquals(
                "x86_64",
                manifest.path("bundleTarget").path("architecture").requireText(),
            )
            assertEquals(
                "Windows x86_64",
                manifest.path("bundleTarget").path("compatibilityLabel").requireText(),
            )
            assertTrue(manifest.path("bundleTarget").path("minimumGlibcVersion").isNull)
            assertEquals(
                listOf(
                    "macos-aarch64",
                    "linux-x86_64",
                    "linux-aarch64",
                    "macos-x86_64",
                    "windows-x86_64",
                ),
                manifest.path("supportedPublicCliBundleTargets").textValues(),
            )
            assertEquals(
                listOf("windows-aarch64"),
                manifest.path("unsupportedPublicCliBundleTargets").textValues(),
            )
            assertEquals("bin/fingrind.ps1", manifest.path("launcher").requireText())
            assertTrue(manifest.path("noExternalJavaRequired").booleanValue())
            assertFalse(
                manifest.path("requiresFingrindSqliteLibraryEnvironmentVariable").booleanValue(),
            )
            assertEquals(
                "sqlite-ffm-sqlite3mc",
                manifest.path("managedSqlite").path("storageDriver").requireText(),
            )
            assertEquals(
                "sqlite",
                manifest.path("managedSqlite").path("storageEngine").requireText(),
            )
            assertEquals(
                "required",
                manifest.path("managedSqlite").path("bookProtectionMode").requireText(),
            )
            assertEquals(
                "chacha20",
                manifest.path("managedSqlite").path("defaultBookCipher").requireText(),
            )
            assertEquals(
                "managed-only",
                manifest.path("managedSqlite").path("libraryMode").requireText(),
            )
            assertEquals(
                "3.53.4",
                manifest.path("managedSqlite").path("requiredMinimumSqliteVersion").requireText(),
            )
            assertEquals(
                "2.4.0",
                manifest.path("managedSqlite").path("requiredSqlite3mcVersion").requireText(),
            )
            assertEquals(
                "2026-04-09 sqlite-source-id",
                manifest.path("managedSqlite").path("requiredSqliteSourceId").requireText(),
            )
            assertEquals(
                listOf("THREADSAFE=1"),
                manifest.path("managedSqlite").path("requiredCompileOptions").textValues(),
            )
            assertEquals(
                listOf("USE_URI"),
                manifest.path("managedSqlite").path("forbiddenCompileOptions").textValues(),
            )
            assertTrue(
                manifest.path("managedSqlite").path("requiresSecureMemorySupport").booleanValue(),
            )
            assertEquals(
                listOf(".\\bin\\fingrind.ps1", "help"),
                manifest.path("bootstrap").path("recommendedFirstCommand").textValues(),
            )
            assertEquals(
                listOf(".\\bin\\fingrind.ps1", "capabilities"),
                manifest.path("bootstrap").path("machineReadableContractCommand").textValues(),
            )
            assertEquals(
                listOf(".\\bin\\fingrind.ps1", "print-request-template"),
                manifest.path("bootstrap").path("requestTemplateCommand").textValues(),
            )
            assertEquals(
                listOf(".\\bin\\fingrind.ps1", "print-plan-template"),
                manifest.path("bootstrap").path("planTemplateCommand").textValues(),
            )
            assertEquals(
                listOf(
                    "README.md",
                    "quick-start-request.json",
                    "bundle-manifest.json",
                    "LICENSE",
                    "LICENSE-APACHE-2.0",
                    "LICENSE-SIL-OFL-1.1",
                    "LICENSE-SQLITE3MULTIPLECIPHERS",
                    "NOTICE",
                    "PATENTS.md",
                ),
                manifest.path("documentationFiles").textValues(),
            )
        } finally {
            DistributionContractReaderTestSupport.deleteTree(repositoryRoot)
        }
    }

    private fun writeManifestContracts(repositoryRoot: Path) {
        DistributionContractReaderTestSupport.writeContractResource(
            repositoryRoot,
            "contract-schema-keys.json",
            DistributionContractReaderTestSupport.contractSchemaKeysJson(),
        )
        DistributionContractReaderTestSupport.writeContractResource(
            repositoryRoot,
            "runtime-surface-contract.json",
            """
            {
              "directJavaRuntimeDistribution": "direct-java-invocation",
              "sourceCheckoutRuntimeDistribution": "source-checkout-gradle",
              "containerRuntimeDistribution": "container-image",
              "bundleRuntimeDistribution": "self-contained-bundle",
              "publicCliDistribution": "self-contained-bundle",
              "storageDriver": "sqlite-ffm-sqlite3mc",
              "storageEngine": "sqlite",
              "bookProtectionMode": "required",
              "defaultBookCipher": "chacha20",
              "sqliteLibraryMode": "managed-only",
              "sqliteBundleHomeSystemProperty": "fingrind.bundle.home"
            }
            """.trimIndent(),
        )
        DistributionContractReaderTestSupport.writeContractResource(
            repositoryRoot,
            "managed-sqlite-contract.json",
            DistributionContractReaderTestSupport.managedSqliteContractJson("", null),
        )
        DistributionContractReaderTestSupport.writeContractResource(
            repositoryRoot,
            "bundle-layout-contract.json",
            DistributionContractReaderTestSupport.sharedBundleLayoutContractJson(),
        )
        DistributionContractReaderTestSupport.writeContractResource(
            repositoryRoot,
            "bundle-publication-contract.json",
            DistributionContractReaderTestSupport.sharedBundlePublicationContractJson(),
        )
        DistributionContractReaderTestSupport.writeContractResource(
            repositoryRoot,
            "operation-id-contract.json",
            """
            {
              "HELP": "help",
              "CAPABILITIES": "capabilities",
              "PRINT_REQUEST_TEMPLATE": "print-request-template",
              "PRINT_PLAN_TEMPLATE": "print-plan-template"
            }
            """.trimIndent(),
        )
    }

}
