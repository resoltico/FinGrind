package dev.erst.fingrind.buildlogic

import java.nio.file.Path

object DistributionContractReader {
    fun publicCliBundleTargets(projectRootDirectory: Path): List<String> =
        DistributionContractModels.bundleLayoutContract(projectRootDirectory)
            .bundleTargets
            .values
            .filter { it.publicBundlePublication.status == PUBLICATION_STATUS_PUBLISHED }
            .map { it.classifier }

    fun unsupportedPublicCliBundleTargets(projectRootDirectory: Path): List<String> =
        DistributionContractModels.bundleLayoutContract(projectRootDirectory)
            .bundleTargets
            .values
            .filter { it.publicBundlePublication.status != PUBLICATION_STATUS_PUBLISHED }
            .map { it.classifier }

    fun requiredMinimumSqliteVersion(projectRootDirectory: Path): String =
        managedSqliteProperty(projectRootDirectory) { it.requiredMinimumSqliteVersion }

    fun requiredSqlite3mcVersion(projectRootDirectory: Path): String =
        managedSqliteProperty(projectRootDirectory) { it.requiredSqlite3mcVersion }

    fun requiredSqliteSourceId(projectRootDirectory: Path): String =
        managedSqliteProperty(projectRootDirectory) { it.requiredSqliteSourceId }

    fun requiredSqliteSourcePackageId(projectRootDirectory: Path): String =
        managedSqliteProperty(projectRootDirectory) { it.requiredSourcePackageId }

    fun vendoredSqliteReleaseFiles(projectRootDirectory: Path): Map<String, String> =
        DistributionContractJson.stringMapProperty(
            projectRootDirectory,
            DistributionContractPaths.MANAGED_SQLITE_CONTRACT_PATH,
            loadContractSchema(projectRootDirectory).managedSqlite.vendoredReleaseFiles,
        )

    fun requiredSqliteCompileOptions(projectRootDirectory: Path): List<String> =
        DistributionContractJson.listProperty(
            projectRootDirectory,
            DistributionContractPaths.MANAGED_SQLITE_CONTRACT_PATH,
            loadContractSchema(projectRootDirectory).managedSqlite.requiredCompileOptions,
            requireExplicitKey = true,
            requireNonEmpty = true,
        )

    fun forbiddenSqliteCompileOptions(projectRootDirectory: Path): List<String> =
        DistributionContractJson.listProperty(
            projectRootDirectory,
            DistributionContractPaths.MANAGED_SQLITE_CONTRACT_PATH,
            loadContractSchema(projectRootDirectory).managedSqlite.forbiddenCompileOptions,
            requireExplicitKey = true,
            requireNonEmpty = true,
        )

    fun requiresSecureMemorySupport(projectRootDirectory: Path): Boolean =
        DistributionContractJson.booleanProperty(
            projectRootDirectory,
            DistributionContractPaths.MANAGED_SQLITE_CONTRACT_PATH,
            loadContractSchema(projectRootDirectory).managedSqlite.requiresSecureMemorySupport,
        )

    fun unixCompilerHardeningFlags(projectRootDirectory: Path): List<String> =
        managedSqliteHardeningList(projectRootDirectory) { it.nativeHardeningUnixCompilerFlags }

    fun linuxLinkerHardeningFlags(projectRootDirectory: Path): List<String> =
        managedSqliteHardeningList(projectRootDirectory) { it.nativeHardeningLinuxLinkerFlags }

    fun macosLinkerHardeningFlags(projectRootDirectory: Path): List<String> =
        managedSqliteHardeningList(projectRootDirectory) { it.nativeHardeningMacosLinkerFlags }

    fun windowsCompilerHardeningFlags(projectRootDirectory: Path): List<String> =
        managedSqliteHardeningList(projectRootDirectory) { it.nativeHardeningWindowsCompilerFlags }

    fun windowsLinkerHardeningFlags(projectRootDirectory: Path): List<String> =
        managedSqliteHardeningList(projectRootDirectory) { it.nativeHardeningWindowsLinkerFlags }

    fun sourceCheckoutRuntimeDistribution(projectRootDirectory: Path): String =
        runtimeSurfaceProperty(projectRootDirectory) { it.sourceCheckoutRuntimeDistribution }

    fun containerRuntimeDistribution(projectRootDirectory: Path): String =
        runtimeSurfaceProperty(projectRootDirectory) { it.containerRuntimeDistribution }

    fun bundleRuntimeDistribution(projectRootDirectory: Path): String =
        runtimeSurfaceProperty(projectRootDirectory) { it.bundleRuntimeDistribution }

    fun publicCliDistribution(projectRootDirectory: Path): String =
        runtimeSurfaceProperty(projectRootDirectory) { it.publicCliDistribution }

    fun storageDriver(projectRootDirectory: Path): String =
        runtimeSurfaceProperty(projectRootDirectory) { it.storageDriver }

    fun storageEngine(projectRootDirectory: Path): String =
        runtimeSurfaceProperty(projectRootDirectory) { it.storageEngine }

    fun bookProtectionMode(projectRootDirectory: Path): String =
        runtimeSurfaceProperty(projectRootDirectory) { it.bookProtectionMode }

    fun defaultBookCipher(projectRootDirectory: Path): String =
        runtimeSurfaceProperty(projectRootDirectory) { it.defaultBookCipher }

    fun sqliteLibraryMode(projectRootDirectory: Path): String =
        runtimeSurfaceProperty(projectRootDirectory) { it.sqliteLibraryMode }

    fun sqliteBundleHomeSystemProperty(projectRootDirectory: Path): String =
        runtimeSurfaceProperty(projectRootDirectory) { it.sqliteBundleHomeSystemProperty }

    fun allowedRuntimeModuleMissingDependencyPrefixes(projectRootDirectory: Path): List<String> =
        DistributionContractJson.listProperty(
            projectRootDirectory,
            DistributionContractPaths.RUNTIME_MODULE_DISCOVERY_CONTRACT_PATH,
            loadContractSchema(projectRootDirectory).runtimeModuleDiscovery.allowedMissingDependencyPrefixes,
            requireExplicitKey = true,
            requireNonEmpty = true,
        )

    fun helpOperationName(projectRootDirectory: Path): String =
        operationIdProperty(projectRootDirectory) { it.help }

    fun capabilitiesOperationName(projectRootDirectory: Path): String =
        operationIdProperty(projectRootDirectory) { it.capabilities }

    fun requestTemplateOperationName(projectRootDirectory: Path): String =
        operationIdProperty(projectRootDirectory) { it.printRequestTemplate }

    fun planTemplateOperationName(projectRootDirectory: Path): String =
        operationIdProperty(projectRootDirectory) { it.printPlanTemplate }

    fun requiredContractFiles(projectRootDirectory: Path): List<Path> =
        DistributionContractPaths.requiredContractFiles(projectRootDirectory)

    internal fun loadContractSchema(projectRootDirectory: Path): ContractSchema =
        DistributionContractSchemas.loadContractSchema(projectRootDirectory)

    private fun runtimeSurfaceProperty(
        projectRootDirectory: Path,
        key: (RuntimeSurfaceSchema) -> String,
    ): String =
        DistributionContractJson.requiredProperty(
            projectRootDirectory,
            DistributionContractPaths.RUNTIME_SURFACE_CONTRACT_PATH,
            key(loadContractSchema(projectRootDirectory).runtimeSurface),
        )

    private fun managedSqliteProperty(
        projectRootDirectory: Path,
        key: (ManagedSqliteSchema) -> String,
    ): String =
        DistributionContractJson.requiredProperty(
            projectRootDirectory,
            DistributionContractPaths.MANAGED_SQLITE_CONTRACT_PATH,
            key(loadContractSchema(projectRootDirectory).managedSqlite),
        )

    private fun managedSqliteHardeningList(
        projectRootDirectory: Path,
        key: (ManagedSqliteSchema) -> String,
    ): List<String> {
        val schema = loadContractSchema(projectRootDirectory).managedSqlite
        val document =
            DistributionContractJson.loadJson(
                projectRootDirectory,
                DistributionContractPaths.MANAGED_SQLITE_CONTRACT_PATH,
            )
        val hardening =
            DistributionContractJson.objectProperty(
                document,
                schema.nativeHardening,
                DistributionContractPaths.MANAGED_SQLITE_CONTRACT_PATH,
            )
        val flagsNode = hardening.path(key(schema))
        if (!flagsNode.isArray) {
            throw IllegalStateException(
                "Expected JSON array contract property ${key(schema)} in ${DistributionContractPaths.MANAGED_SQLITE_CONTRACT_PATH}.",
            )
        }
        val flags = linkedSetOf<String>()
        flagsNode.forEach { node ->
            val value = if (node.isString) node.stringValue()?.trim().orEmpty() else ""
            if (value.isEmpty()) {
                throw IllegalStateException(
                    "Expected JSON string elements in contract property ${key(schema)} in ${DistributionContractPaths.MANAGED_SQLITE_CONTRACT_PATH}.",
                )
            }
            if (!flags.add(value)) {
                throw IllegalStateException(
                    "Duplicate contract list element $value in ${key(schema)} from ${DistributionContractPaths.MANAGED_SQLITE_CONTRACT_PATH}.",
                )
            }
        }
        return flags.toList()
    }

    private fun operationIdProperty(
        projectRootDirectory: Path,
        key: (OperationIdSchema) -> String,
    ): String =
        DistributionContractJson.requiredProperty(
            projectRootDirectory,
            DistributionContractPaths.OPERATION_ID_CONTRACT_PATH,
            key(loadContractSchema(projectRootDirectory).operationIds),
        )

    internal data class ContractSchema(
        val runtimeSurface: RuntimeSurfaceSchema,
        val runtimeModuleDiscovery: RuntimeModuleDiscoverySchema,
        val managedSqlite: ManagedSqliteSchema,
        val bundleLayout: BundleLayoutSchema,
        val bundlePublication: BundlePublicationSchema,
        val operationIds: OperationIdSchema,
    )

    internal data class RuntimeSurfaceSchema(
        val directJavaRuntimeDistribution: String,
        val sourceCheckoutRuntimeDistribution: String,
        val containerRuntimeDistribution: String,
        val bundleRuntimeDistribution: String,
        val publicCliDistribution: String,
        val storageDriver: String,
        val storageEngine: String,
        val bookProtectionMode: String,
        val defaultBookCipher: String,
        val sqliteLibraryMode: String,
        val sqliteBundleHomeSystemProperty: String,
    )

    internal data class RuntimeModuleDiscoverySchema(
        val allowedMissingDependencyPrefixes: String,
    )

    internal data class ManagedSqliteSchema(
        val requiredMinimumSqliteVersion: String,
        val requiredSqlite3mcVersion: String,
        val requiredSqliteSourceId: String,
        val requiredSourcePackageId: String,
        val vendoredReleaseFiles: String,
        val nativeHardening: String,
        val nativeHardeningUnixCompilerFlags: String,
        val nativeHardeningLinuxLinkerFlags: String,
        val nativeHardeningMacosLinkerFlags: String,
        val nativeHardeningWindowsCompilerFlags: String,
        val nativeHardeningWindowsLinkerFlags: String,
        val requiredCompileOptions: String,
        val forbiddenCompileOptions: String,
        val requiresSecureMemorySupport: String,
    )

    internal data class OperationIdSchema(
        val help: String,
        val capabilities: String,
        val printRequestTemplate: String,
        val printPlanTemplate: String,
    )
}
