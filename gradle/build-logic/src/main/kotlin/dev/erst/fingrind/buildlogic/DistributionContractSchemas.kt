package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import tools.jackson.databind.JsonNode

internal object DistributionContractSchemas {
    fun loadContractSchema(projectRootDirectory: Path): DistributionContractReader.ContractSchema {
        val document = DistributionContractJson.loadJson(projectRootDirectory, DistributionContractPaths.CONTRACT_SCHEMA_KEYS_PATH)
        return DistributionContractReader.ContractSchema(
            runtimeSurface = DistributionContractReader.RuntimeSurfaceSchema(
                directJavaRuntimeDistribution = schemaKey(document, "runtimeSurface", "directJavaRuntimeDistribution"),
                sourceCheckoutRuntimeDistribution =
                    schemaKey(document, "runtimeSurface", "sourceCheckoutRuntimeDistribution"),
                containerRuntimeDistribution =
                    schemaKey(document, "runtimeSurface", "containerRuntimeDistribution"),
                bundleRuntimeDistribution = schemaKey(document, "runtimeSurface", "bundleRuntimeDistribution"),
                publicCliDistribution = schemaKey(document, "runtimeSurface", "publicCliDistribution"),
                storageDriver = schemaKey(document, "runtimeSurface", "storageDriver"),
                storageEngine = schemaKey(document, "runtimeSurface", "storageEngine"),
                bookProtectionMode = schemaKey(document, "runtimeSurface", "bookProtectionMode"),
                defaultBookCipher = schemaKey(document, "runtimeSurface", "defaultBookCipher"),
                sqliteLibraryMode = schemaKey(document, "runtimeSurface", "sqliteLibraryMode"),
                sqliteLibraryEnvironmentVariable =
                    schemaKey(document, "runtimeSurface", "sqliteLibraryEnvironmentVariable"),
                sqliteOperatorTrustSystemProperty =
                    schemaKey(document, "runtimeSurface", "sqliteOperatorTrustSystemProperty"),
                sqliteBundleHomeSystemProperty =
                    schemaKey(document, "runtimeSurface", "sqliteBundleHomeSystemProperty"),
            ),
            publicDistribution = DistributionContractReader.PublicDistributionSchema(
                supportedPublicCliBundleTargets =
                    schemaKey(document, "publicDistribution", "supportedPublicCliBundleTargets"),
                unsupportedPublicCliBundleTargets =
                    schemaKey(document, "publicDistribution", "unsupportedPublicCliBundleTargets"),
            ),
            managedSqlite = DistributionContractReader.ManagedSqliteSchema(
                requiredMinimumSqliteVersion =
                    schemaKey(document, "managedSqlite", "requiredMinimumSqliteVersion"),
                requiredSqlite3mcVersion =
                    schemaKey(document, "managedSqlite", "requiredSqlite3mcVersion"),
                requiredSqliteSourceId =
                    schemaKey(document, "managedSqlite", "requiredSqliteSourceId"),
                requiredSourcePackageId =
                    schemaKey(document, "managedSqlite", "requiredSourcePackageId"),
                vendoredReleaseFiles =
                    schemaKey(document, "managedSqlite", "vendoredReleaseFiles"),
                nativeHardening = schemaKey(document, "managedSqlite", "nativeHardening"),
                nativeHardeningUnixCompilerFlags =
                    schemaKey(document, "managedSqlite", "nativeHardeningUnixCompilerFlags"),
                nativeHardeningLinuxLinkerFlags =
                    schemaKey(document, "managedSqlite", "nativeHardeningLinuxLinkerFlags"),
                nativeHardeningMacosLinkerFlags =
                    schemaKey(document, "managedSqlite", "nativeHardeningMacosLinkerFlags"),
                nativeHardeningWindowsCompilerFlags =
                    schemaKey(document, "managedSqlite", "nativeHardeningWindowsCompilerFlags"),
                nativeHardeningWindowsLinkerFlags =
                    schemaKey(document, "managedSqlite", "nativeHardeningWindowsLinkerFlags"),
                requiredCompileOptions =
                    schemaKey(document, "managedSqlite", "requiredCompileOptions"),
                forbiddenCompileOptions =
                    schemaKey(document, "managedSqlite", "forbiddenCompileOptions"),
                requiresSecureMemorySupport =
                    schemaKey(document, "managedSqlite", "requiresSecureMemorySupport"),
            ),
            bundleLayout = DistributionContractReader.BundleLayoutSchema(
                bundleTargets = schemaKey(document, "bundleLayout", "bundleTargets"),
                operatingSystemId = schemaKey(document, "bundleLayout", "operatingSystemId"),
                architectureId = schemaKey(document, "bundleLayout", "architectureId"),
                archiveFormat = schemaKey(document, "bundleLayout", "archiveFormat"),
                launcherPath = schemaKey(document, "bundleLayout", "launcherPath"),
                launcherCommand = schemaKey(document, "bundleLayout", "launcherCommand"),
                sqliteLibraryFileName =
                    schemaKey(document, "bundleLayout", "sqliteLibraryFileName"),
            ),
            operationIds = DistributionContractReader.OperationIdSchema(
                help = schemaKey(document, "operationIdContract", "help"),
                capabilities = schemaKey(document, "operationIdContract", "capabilities"),
                printRequestTemplate = schemaKey(document, "operationIdContract", "printRequestTemplate"),
                printPlanTemplate = schemaKey(document, "operationIdContract", "printPlanTemplate"),
            ),
        )
    }

    private fun schemaKey(document: JsonNode, objectKey: String, fieldKey: String): String {
        val objectNode = document.path(objectKey)
        if (!objectNode.isObject) {
            throw IllegalStateException("Contract schema key object $objectKey must exist in ${DistributionContractPaths.CONTRACT_SCHEMA_KEYS_PATH}.")
        }
        val keyNode = objectNode.path(fieldKey)
        val key = if (keyNode.isString) keyNode.stringValue()?.trim().orEmpty() else ""
        if (key.isEmpty()) {
            throw IllegalStateException(
                "Contract schema key $objectKey.$fieldKey must be one non-blank JSON string in ${DistributionContractPaths.CONTRACT_SCHEMA_KEYS_PATH}.",
            )
        }
        return key
    }
}
