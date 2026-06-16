package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class BundleManifestRendererTest {
    private val objectMapper = JsonMapper.builder().build()

    @Test
    fun renderBundleManifest_derivesCanonicalRuntimeAndBootstrapFacts() {
        val repositoryRoot = Files.createTempDirectory("bundle-manifest-renderer")
        try {
            writeContractResource(
                repositoryRoot,
                "contract-schema-keys.json",
                """
                {
                  "runtimeSurface": {
                    "directJavaRuntimeDistribution": "directJavaDist",
                    "sourceCheckoutRuntimeDistribution": "sourceDist",
                    "containerRuntimeDistribution": "containerDist",
                    "bundleRuntimeDistribution": "bundleDist",
                    "publicCliDistribution": "publicDist",
                    "storageDriver": "driver",
                    "storageEngine": "engine",
                    "bookProtectionMode": "protection",
                    "defaultBookCipher": "cipher",
                    "sqliteLibraryMode": "libraryMode",
                    "sqliteBundleHomeSystemProperty": "bundleHome"
                  },
                  "runtimeModuleDiscovery": {
                    "allowedMissingDependencyPrefixes": "allowedMissingDependencyPrefixes"
                  },
                  "managedSqlite": {
                    "requiredMinimumSqliteVersion": "minimumSqliteVersion",
                    "requiredSqlite3mcVersion": "sqlite3mcVersion",
                    "requiredSqliteSourceId": "sqliteSourceId",
                    "requiredSourcePackageId": "sourcePackageId",
                    "vendoredReleaseFiles": "vendoredReleaseFiles",
                    "nativeHardening": "nativeHardening",
                    "nativeHardeningUnixCompilerFlags": "unixCompilerFlags",
                    "nativeHardeningLinuxLinkerFlags": "linuxLinkerFlags",
                    "nativeHardeningMacosLinkerFlags": "macosLinkerFlags",
                    "nativeHardeningWindowsCompilerFlags": "windowsCompilerFlags",
                    "nativeHardeningWindowsLinkerFlags": "windowsLinkerFlags",
                    "requiredCompileOptions": "compileOptions",
                    "forbiddenCompileOptions": "forbiddenCompileOptions",
                    "requiresSecureMemorySupport": "requiresSecureMemorySupport"
                  },
                  "bundleLayout": {
                    "bundleTargets": "bundleTargets",
                    "operatingSystemId": "operatingSystemId",
                    "architectureId": "architectureId",
                    "archiveFormat": "archiveFormat",
                    "launcherPath": "launcherPath",
                    "launcherCommand": "launcherCommand",
                    "sqliteLibraryFileName": "sqliteLibraryFileName",
                    "compatibilityLabel": "compatibilityLabel",
                    "minimumGlibcVersion": "minimumGlibcVersion",
                    "compatibilitySmokeContainerImage": "compatibilitySmokeContainerImage"
                  },
                  "bundlePublication": {
                    "bundleTargets": "bundleTargets",
                    "status": "status",
                    "runnerLabel": "runnerLabel"
                  },
                  "operationIdContract": {
                    "help": "OP_HELP",
                    "capabilities": "OP_CAP",
                    "printRequestTemplate": "OP_REQUEST",
                    "printPlanTemplate": "OP_PLAN"
                  }
                }
                """.trimIndent(),
            )
            writeContractResource(
                repositoryRoot,
                "runtime-surface-contract.json",
                """
                {
                  "directJavaDist": "direct-java-invocation",
                  "sourceDist": "source-checkout-gradle",
                  "containerDist": "container-image",
                  "bundleDist": "self-contained-bundle",
                  "publicDist": "self-contained-bundle",
                  "driver": "sqlite-ffm-sqlite3mc",
                  "engine": "sqlite",
                  "protection": "required",
                  "cipher": "chacha20",
                  "libraryMode": "managed-only",
                  "libraryEnv": "FINGRIND_SQLITE_LIBRARY",
                  "bundleHome": "fingrind.bundle.home"
                }
                """.trimIndent(),
            )
            writeContractResource(
                repositoryRoot,
                "managed-sqlite-contract.json",
                """
                {
                  "minimumSqliteVersion": "3.53.2",
                  "sqlite3mcVersion": "2.3.5",
                  "sqliteSourceId": "2026-04-09 sqlite-source-id",
                  "sourcePackageId": "sqlite3mc-amalgamation-test",
                  "vendoredReleaseFiles": {
                    "sqlite3mc_amalgamation.c": "sha3-a"
                  },
                  "nativeHardening": {
                    "unixCompilerFlags": ["-fstack-protector-strong"],
                    "linuxLinkerFlags": ["-Wl,-z,relro"],
                    "macosLinkerFlags": [],
                    "windowsCompilerFlags": ["/GS"],
                    "windowsLinkerFlags": ["/NXCOMPAT"]
                  },
                  "compileOptions": ["THREADSAFE=1", "SECURE_DELETE"],
                  "forbiddenCompileOptions": ["USE_URI"],
                  "requiresSecureMemorySupport": true
                }
                """.trimIndent(),
            )
            writeContractResource(
                repositoryRoot,
                "bundle-layout-contract.json",
                DistributionContractReaderTestSupport.sharedBundleLayoutContractJson(),
            )
            writeContractResource(
                repositoryRoot,
                "bundle-publication-contract.json",
                DistributionContractReaderTestSupport.sharedBundlePublicationContractJson(),
            )
            writeContractResource(
                repositoryRoot,
                "operation-id-contract.json",
                """
                {
                  "OP_HELP": "help",
                  "OP_CAP": "capabilities",
                  "OP_REQUEST": "print-request-template",
                  "OP_PLAN": "print-plan-template"
                }
                """.trimIndent(),
            )

            val rendered =
                BundleManifestRenderer.renderBundleManifest(
                    repositoryRoot,
                    applicationName = "FinGrind",
                    version = "0.26.0",
                    bundleClassifier = "linux-x86_64",
                    normalizedArtifactTimestampUtc = "2026-06-14T16:43:08Z",
                )

            val manifest = objectMapper.readTree(rendered)
            assertEquals("FinGrind", manifest.path("application").requireText())
            assertEquals("0.26.0", manifest.path("version").requireText())
            assertEquals(
                "2026-06-14T16:43:08Z",
                manifest.path("normalizedArtifactTimestampUtc").requireText(),
            )
            assertEquals("self-contained-cli-bundle", manifest.path("artifactType").requireText())
            assertEquals("tar.gz", manifest.path("archiveFormat").requireText())
            assertEquals("self-contained-bundle", manifest.path("runtimeDistribution").requireText())
            assertEquals("self-contained-bundle", manifest.path("publicCliDistribution").requireText())
            assertEquals("linux-x86_64", manifest.path("bundleTarget").path("classifier").requireText())
            assertEquals("linux", manifest.path("bundleTarget").path("operatingSystem").requireText())
            assertEquals("x86_64", manifest.path("bundleTarget").path("architecture").requireText())
            assertEquals(
                "glibc 2.34+ Linux x86_64",
                manifest.path("bundleTarget").path("compatibilityLabel").requireText(),
            )
            assertEquals(
                "2.34",
                manifest.path("bundleTarget").path("minimumGlibcVersion").requireText(),
            )
            assertEquals("bin/fingrind", manifest.path("launcher").requireText())
            assertEquals(
                "sqlite-ffm-sqlite3mc",
                manifest.path("managedSqlite").path("storageDriver").requireText(),
            )
            assertEquals(
                "3.53.2",
                manifest.path("managedSqlite").path("requiredMinimumSqliteVersion").requireText(),
            )
            assertEquals(
                "2.3.5",
                manifest.path("managedSqlite").path("requiredSqlite3mcVersion").requireText(),
            )
            assertEquals(
                "2026-04-09 sqlite-source-id",
                manifest.path("managedSqlite").path("requiredSqliteSourceId").requireText(),
            )
            assertEquals(
                listOf("THREADSAFE=1", "SECURE_DELETE"),
                manifest.path("managedSqlite").path("requiredCompileOptions").toList().map { it.requireText() },
            )
            assertEquals(
                listOf("USE_URI"),
                manifest.path("managedSqlite").path("forbiddenCompileOptions").toList().map { it.requireText() },
            )
            assertTrue(
                manifest.path("managedSqlite").path("requiresSecureMemorySupport").booleanValue(),
            )
            assertEquals(
                "print-plan-template",
                manifest.path("bootstrap").path("planTemplateCommand").get(1).requireText(),
            )
            val supportedTargets =
                manifest.path("supportedPublicCliBundleTargets").toList().map { it.requireText() }
            assertEquals(
                listOf(
                    "macos-aarch64",
                    "linux-x86_64",
                    "linux-aarch64",
                    "macos-x86_64",
                    "windows-x86_64",
                ),
                supportedTargets,
            )
            val unsupportedTargets =
                manifest.path("unsupportedPublicCliBundleTargets").toList().map { it.requireText() }
            assertEquals(
                listOf("windows-aarch64"),
                unsupportedTargets,
            )
            val documentationFiles =
                manifest.path("documentationFiles").toList().map { it.requireText() }
            assertTrue(
                documentationFiles.contains("bundle-manifest.json"),
            )
            assertTrue(
                documentationFiles.contains("quick-start-request.json"),
            )
            assertFalse(rendered.contains("discoveryCommands"))
        } finally {
            deleteTree(repositoryRoot)
        }
    }

    private fun writeContractResource(
        repositoryRoot: Path,
        fileName: String,
        contents: String,
    ) {
        val path =
            repositoryRoot.resolve(
                "contract/src/main/resources/dev/erst/fingrind/contract/protocol/$fileName",
            )
        Files.createDirectories(path.parent)
        Files.writeString(path, contents)
    }

    private fun deleteTree(root: Path) {
        Files.walk(root)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::deleteIfExists)
    }

    private fun JsonNode.requireText(): String = requireNotNull(asString()) { "Expected text node: $this" }
}
