package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class DistributionContractReaderTest {
    @Test
    fun sharedSchemaKeys_drivePublicDistributionRuntimeSurfaceAndOperationLookups() {
        val repositoryRoot = Files.createTempDirectory("distribution-contract-reader")
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
                  "publicDistribution": {
                    "supportedPublicCliBundleTargets": "supportedTargets",
                    "unsupportedPublicCliBundleTargets": "unsupportedTargets"
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
                    "sqliteLibraryFileName": "sqliteLibraryFileName"
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
                  "bundleHome": "fingrind.bundle.home"
                }
                """.trimIndent(),
            )
            writeContractResource(
                repositoryRoot,
                "public-distribution-contract.json",
                """
                {
                  "supportedTargets": ["macos-aarch64", "linux-x86_64"],
                  "unsupportedTargets": ["windows-aarch64"]
                }
                """.trimIndent(),
            )
            writeContractResource(
                repositoryRoot,
                "managed-sqlite-contract.json",
                """
                {
                  "minimumSqliteVersion": "3.53.1",
                  "sqlite3mcVersion": "2.3.4",
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
                """
                {
                  "bundleTargets": {
                    "macos-aarch64": {
                      "operatingSystemId": "macos",
                      "architectureId": "aarch64",
                      "archiveFormat": "tar.gz",
                      "launcherPath": "bin/fingrind",
                      "launcherCommand": "./bin/fingrind",
                      "sqliteLibraryFileName": "libsqlite3.dylib"
                    },
                    "linux-x86_64": {
                      "operatingSystemId": "linux",
                      "architectureId": "x86_64",
                      "archiveFormat": "tar.gz",
                      "launcherPath": "bin/fingrind",
                      "launcherCommand": "./bin/fingrind",
                      "sqliteLibraryFileName": "libsqlite3.so.0"
                    },
                    "windows-aarch64": {
                      "operatingSystemId": "windows",
                      "architectureId": "aarch64",
                      "archiveFormat": "zip",
                      "launcherPath": "bin/fingrind.ps1",
                      "launcherCommand": ".\\bin\\fingrind.ps1",
                      "sqliteLibraryFileName": "sqlite3.dll"
                    }
                  }
                }
                """.trimIndent(),
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
            val nestedBuildRoot = repositoryRoot.resolve("jazzer")
            nestedBuildRoot.createDirectories()

            assertEquals(
                listOf("macos-aarch64", "linux-x86_64"),
                DistributionContractReader.publicCliBundleTargets(repositoryRoot),
            )
            assertEquals(
                listOf("windows-aarch64"),
                DistributionContractReader.unsupportedPublicCliBundleTargets(repositoryRoot),
            )
            assertEquals(
                "source-checkout-gradle",
                DistributionContractReader.sourceCheckoutRuntimeDistribution(repositoryRoot),
            )
            assertEquals(
                "container-image",
                DistributionContractReader.containerRuntimeDistribution(repositoryRoot),
            )
            assertEquals(
                "self-contained-bundle",
                DistributionContractReader.bundleRuntimeDistribution(repositoryRoot),
            )
            assertEquals(
                "self-contained-bundle",
                DistributionContractReader.publicCliDistribution(repositoryRoot),
            )
            assertEquals("sqlite-ffm-sqlite3mc", DistributionContractReader.storageDriver(repositoryRoot))
            assertEquals("sqlite", DistributionContractReader.storageEngine(repositoryRoot))
            assertEquals("required", DistributionContractReader.bookProtectionMode(repositoryRoot))
            assertEquals("chacha20", DistributionContractReader.defaultBookCipher(repositoryRoot))
            assertEquals("managed-only", DistributionContractReader.sqliteLibraryMode(repositoryRoot))
            assertEquals(
                "fingrind.bundle.home",
                DistributionContractReader.sqliteBundleHomeSystemProperty(repositoryRoot),
            )
            assertEquals(
                "3.53.1",
                DistributionContractReader.requiredMinimumSqliteVersion(repositoryRoot),
            )
            assertEquals(
                "2.3.4",
                DistributionContractReader.requiredSqlite3mcVersion(repositoryRoot),
            )
            assertEquals(
                "2026-04-09 sqlite-source-id",
                DistributionContractReader.requiredSqliteSourceId(repositoryRoot),
            )
            assertEquals(
                "sqlite3mc-amalgamation-test",
                DistributionContractReader.requiredSqliteSourcePackageId(repositoryRoot),
            )
            assertEquals(
                mapOf("sqlite3mc_amalgamation.c" to "sha3-a"),
                DistributionContractReader.vendoredSqliteReleaseFiles(repositoryRoot),
            )
            assertEquals(
                listOf("THREADSAFE=1", "SECURE_DELETE"),
                DistributionContractReader.requiredSqliteCompileOptions(repositoryRoot),
            )
            assertEquals(
                listOf("USE_URI"),
                DistributionContractReader.forbiddenSqliteCompileOptions(repositoryRoot),
            )
            assertEquals(
                true,
                DistributionContractReader.requiresSecureMemorySupport(repositoryRoot),
            )
            assertEquals(
                listOf("-fstack-protector-strong"),
                DistributionContractReader.unixCompilerHardeningFlags(repositoryRoot),
            )
            assertEquals(
                listOf("-Wl,-z,relro"),
                DistributionContractReader.linuxLinkerHardeningFlags(repositoryRoot),
            )
            assertEquals(
                emptyList(),
                DistributionContractReader.macosLinkerHardeningFlags(repositoryRoot),
            )
            assertEquals(
                listOf("/GS"),
                DistributionContractReader.windowsCompilerHardeningFlags(repositoryRoot),
            )
            assertEquals(
                listOf("/NXCOMPAT"),
                DistributionContractReader.windowsLinkerHardeningFlags(repositoryRoot),
            )
            assertEquals(
                DistributionContractReader.BundleTargetContract(
                    classifier = "windows-aarch64",
                    operatingSystemId = "windows",
                    architectureId = "aarch64",
                    archiveFormat = "zip",
                    launcherPath = "bin/fingrind.ps1",
                    launcherCommand = ".\\bin\\fingrind.ps1",
                    sqliteLibraryFileName = "sqlite3.dll",
                ),
                DistributionContractReader.hostBundleTarget(
                    repositoryRoot,
                    osName = "Windows 11",
                    architecture = "ARM64",
                ),
            )
            assertEquals(
                "windows-aarch64",
                DistributionContractReader.hostClassifier(
                    repositoryRoot,
                    osName = "Windows 11",
                    architecture = "ARM64",
                ),
            )
            assertEquals("help", DistributionContractReader.helpOperationName(repositoryRoot))
            assertEquals("capabilities", DistributionContractReader.capabilitiesOperationName(repositoryRoot))
            assertEquals(
                "print-request-template",
                DistributionContractReader.requestTemplateOperationName(repositoryRoot),
            )
            assertEquals("print-plan-template", DistributionContractReader.planTemplateOperationName(repositoryRoot))
            assertEquals(
                "3.53.1",
                DistributionContractReader.requiredMinimumSqliteVersion(nestedBuildRoot),
            )
            assertEquals(
                "windows-aarch64",
                DistributionContractReader.hostClassifier(
                    nestedBuildRoot,
                    osName = "Windows 11",
                    architecture = "ARM64",
                ),
            )
        } finally {
            deleteTree(repositoryRoot)
        }
    }

    @Test
    fun publicDistributionContract_mustClassifyEveryDeclaredBundleTarget() {
        val repositoryRoot = Files.createTempDirectory("distribution-contract-reader-missing-target")
        try {
            writeContractResource(
                repositoryRoot,
                "contract-schema-keys.json",
                """
                {
                  "runtimeSurface": {
                    "directJavaRuntimeDistribution": "directJavaRuntimeDistribution",
                    "sourceCheckoutRuntimeDistribution": "sourceCheckoutRuntimeDistribution",
                    "containerRuntimeDistribution": "containerRuntimeDistribution",
                    "bundleRuntimeDistribution": "bundleRuntimeDistribution",
                    "publicCliDistribution": "publicCliDistribution",
                    "storageDriver": "storageDriver",
                    "storageEngine": "storageEngine",
                    "bookProtectionMode": "bookProtectionMode",
                    "defaultBookCipher": "defaultBookCipher",
                    "sqliteLibraryMode": "sqliteLibraryMode",
                    "sqliteBundleHomeSystemProperty": "sqliteBundleHomeSystemProperty"
                  },
                  "publicDistribution": {
                    "supportedPublicCliBundleTargets": "supportedTargets",
                    "unsupportedPublicCliBundleTargets": "unsupportedTargets"
                  },
                  "managedSqlite": {
                    "requiredMinimumSqliteVersion": "requiredMinimumSqliteVersion",
                    "requiredSqlite3mcVersion": "requiredSqlite3mcVersion",
                    "requiredSqliteSourceId": "requiredSqliteSourceId",
                    "requiredSourcePackageId": "requiredSourcePackageId",
                    "vendoredReleaseFiles": "vendoredReleaseFiles",
                    "nativeHardening": "nativeHardening",
                    "nativeHardeningUnixCompilerFlags": "unixCompilerFlags",
                    "nativeHardeningLinuxLinkerFlags": "linuxLinkerFlags",
                    "nativeHardeningMacosLinkerFlags": "macosLinkerFlags",
                    "nativeHardeningWindowsCompilerFlags": "windowsCompilerFlags",
                    "nativeHardeningWindowsLinkerFlags": "windowsLinkerFlags",
                    "requiredCompileOptions": "requiredCompileOptions",
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
                    "sqliteLibraryFileName": "sqliteLibraryFileName"
                  },
                  "operationIdContract": {
                    "help": "HELP",
                    "capabilities": "CAPABILITIES",
                    "printRequestTemplate": "PRINT_REQUEST_TEMPLATE",
                    "printPlanTemplate": "PRINT_PLAN_TEMPLATE"
                  }
                }
                """.trimIndent(),
            )
            writeContractResource(
                repositoryRoot,
                "public-distribution-contract.json",
                """
                {
                  "supportedTargets": ["linux-x86_64"],
                  "unsupportedTargets": []
                }
                """.trimIndent(),
            )
            writeContractResource(
                repositoryRoot,
                "bundle-layout-contract.json",
                """
                {
                  "bundleTargets": {
                    "linux-x86_64": {
                      "operatingSystemId": "linux",
                      "architectureId": "x86_64",
                      "archiveFormat": "tar.gz",
                      "launcherPath": "bin/fingrind",
                      "launcherCommand": "./bin/fingrind",
                      "sqliteLibraryFileName": "libsqlite3.so.0"
                    },
                    "windows-aarch64": {
                      "operatingSystemId": "windows",
                      "architectureId": "aarch64",
                      "archiveFormat": "zip",
                      "launcherPath": "bin/fingrind.ps1",
                      "launcherCommand": ".\\bin\\fingrind.ps1",
                      "sqliteLibraryFileName": "sqlite3.dll"
                    }
                  }
                }
                """.trimIndent(),
            )

            val exception =
                kotlin.test.assertFailsWith<IllegalStateException> {
                    DistributionContractReader.publicCliBundleTargets(repositoryRoot)
                }

            assertEquals(
                "Public distribution contract must classify every declared bundle target. Missing: windows-aarch64",
                exception.message,
            )
        } finally {
            deleteTree(repositoryRoot)
        }
    }

    private fun writeContractResource(repositoryRoot: Path, fileName: String, contents: String) {
        val path =
            repositoryRoot.resolve("contract/src/main/resources/dev/erst/fingrind/contract/protocol/$fileName")
        path.parent.createDirectories()
        path.writeText(contents + System.lineSeparator())
    }

    private fun deleteTree(rootPath: Path) {
        if (!Files.exists(rootPath)) {
            return
        }
        Files.walk(rootPath).use { pathStream ->
            pathStream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
