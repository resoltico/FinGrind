package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal object DistributionContractReaderTestSupport {
    val managedSqliteContractPath: String = DistributionContractPaths.MANAGED_SQLITE_CONTRACT_PATH

    fun writeContractResource(repositoryRoot: Path, fileName: String, contents: String) {
        val path =
            repositoryRoot.resolve("contract/src/main/resources/dev/erst/fingrind/contract/protocol/$fileName")
        path.parent.createDirectories()
        path.writeText(contents + System.lineSeparator())
    }

    fun assertCompileOptionListFailure(
        fieldName: String,
        fieldJson: String?,
        expectedMessage: String,
        reader: (Path) -> List<String>,
    ) {
        val repositoryRoot = Files.createTempDirectory("distribution-contract-reader-compile-options")
        try {
            writeContractResource(repositoryRoot, "contract-schema-keys.json", contractSchemaKeysJson())
            writeContractResource(
                repositoryRoot,
                "managed-sqlite-contract.json",
                managedSqliteContractJson(fieldName, fieldJson),
            )
            val exception = assertFailsWith<IllegalStateException> { reader(repositoryRoot) }
            assertEquals(expectedMessage, exception.message)
        } finally {
            deleteTree(repositoryRoot)
        }
    }

    fun sharedSchemaKeysJson(): String =
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
        """.trimIndent()

    fun contractSchemaKeysJson(): String =
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
          "runtimeModuleDiscovery": {
            "allowedMissingDependencyPrefixes": "allowedMissingDependencyPrefixes"
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
        """.trimIndent()

    fun managedSqliteContractJson(fieldName: String, fieldJson: String?): String {
        val compileOptionsEntry =
            if (fieldName == "requiredCompileOptions" && fieldJson != null) {
                """"requiredCompileOptions": $fieldJson,"""
            } else if (fieldName == "requiredCompileOptions") {
                ""
            } else {
                """"requiredCompileOptions": ["THREADSAFE=1"],"""
            }
        val forbiddenOptionsEntry =
            if (fieldName == "forbiddenCompileOptions" && fieldJson != null) {
                """"forbiddenCompileOptions": $fieldJson,"""
            } else if (fieldName == "forbiddenCompileOptions") {
                ""
            } else {
                """"forbiddenCompileOptions": ["USE_URI"],"""
            }
        return """
            {
              "requiredMinimumSqliteVersion": "3.53.1",
              "requiredSqlite3mcVersion": "2.3.4",
              "requiredSqliteSourceId": "2026-04-09 sqlite-source-id",
              "requiredSourcePackageId": "sqlite3mc-amalgamation-test",
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
              $compileOptionsEntry
              $forbiddenOptionsEntry
              "requiresSecureMemorySupport": true
            }
        """.trimIndent()
    }

    fun runtimeModuleDiscoveryContractJson(fieldName: String, fieldJson: String?): String {
        val allowedMissingDependencyPrefixesEntry =
            if (fieldName == "allowedMissingDependencyPrefixes" && fieldJson != null) {
                """"allowedMissingDependencyPrefixes": $fieldJson"""
            } else if (fieldName == "allowedMissingDependencyPrefixes") {
                ""
            } else {
                """"allowedMissingDependencyPrefixes": ["org.apache.logging.log4j."]"""
            }
        return """
            {
              $allowedMissingDependencyPrefixesEntry
            }
        """.trimIndent()
    }

    fun deleteTree(rootPath: Path) {
        if (!Files.exists(rootPath)) {
            return
        }
        Files.walk(rootPath).use { pathStream ->
            pathStream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
