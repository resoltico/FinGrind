package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import tools.jackson.databind.json.JsonMapper

object BundleManifestRenderer {
    private val objectMapper = JsonMapper.builder().build()

    fun renderBundleManifest(
        projectRootDirectory: Path,
        applicationName: String,
        version: String,
        bundleClassifier: String,
        normalizedArtifactTimestampUtc: String,
    ): String {
        val checkedApplicationName =
            BundleStagingContractValidation.requireNonBlank(
                applicationName,
                "Bundle manifest application name",
            )
        val checkedVersion =
            BundleStagingContractValidation.requireNonBlank(version, "Bundle manifest version")
        val checkedBundleClassifier =
            BundleStagingContractValidation.requireNonBlank(
                bundleClassifier,
                "Bundle manifest bundle classifier",
            )
        val checkedTimestamp =
            BundleStagingContractValidation.requireNonBlank(
                normalizedArtifactTimestampUtc,
                "Bundle manifest normalized artifact timestamp",
            )
        val bundleTarget =
            DistributionBundleTargetReader.bundleTarget(projectRootDirectory, checkedBundleClassifier)
        val bundleStagingLayout =
            BundleStagingLayout.plan(
                version = checkedVersion,
                bundleTarget = bundleTarget,
            )
        val document =
            BundleManifestDocument(
                application = checkedApplicationName,
                version = checkedVersion,
                normalizedArtifactTimestampUtc = checkedTimestamp,
                artifactType = "self-contained-cli-bundle",
                archiveFormat = bundleStagingLayout.archiveFormat,
                runtimeDistribution =
                    DistributionContractReader.bundleRuntimeDistribution(projectRootDirectory),
                publicCliDistribution =
                    DistributionContractReader.publicCliDistribution(projectRootDirectory),
                bundleTarget =
                    BundleTargetDocument(
                        classifier = bundleTarget.classifier,
                        operatingSystem = bundleTarget.operatingSystemId,
                        architecture = bundleTarget.architectureId,
                        compatibilityLabel = bundleTarget.compatibilityLabel,
                        minimumGlibcVersion = bundleTarget.minimumGlibcVersion,
                    ),
                supportedPublicCliBundleTargets =
                    DistributionContractReader.publicCliBundleTargets(projectRootDirectory),
                unsupportedPublicCliBundleTargets =
                    DistributionContractReader.unsupportedPublicCliBundleTargets(projectRootDirectory),
                launcher = bundleStagingLayout.launcherPath,
                noExternalJavaRequired = true,
                requiresFingrindSqliteLibraryEnvironmentVariable = false,
                managedSqlite =
                    ManagedSqliteDocument(
                        storageDriver = DistributionContractReader.storageDriver(projectRootDirectory),
                        storageEngine = DistributionContractReader.storageEngine(projectRootDirectory),
                        bookProtectionMode =
                            DistributionContractReader.bookProtectionMode(projectRootDirectory),
                        defaultBookCipher =
                            DistributionContractReader.defaultBookCipher(projectRootDirectory),
                        libraryMode = DistributionContractReader.sqliteLibraryMode(projectRootDirectory),
                        requiredMinimumSqliteVersion =
                            DistributionContractReader.requiredMinimumSqliteVersion(projectRootDirectory),
                        requiredSqlite3mcVersion =
                            DistributionContractReader.requiredSqlite3mcVersion(projectRootDirectory),
                        requiredSqliteSourceId =
                            DistributionContractReader.requiredSqliteSourceId(projectRootDirectory),
                        requiredCompileOptions =
                            DistributionContractReader.requiredSqliteCompileOptions(projectRootDirectory),
                        forbiddenCompileOptions =
                            DistributionContractReader.forbiddenSqliteCompileOptions(
                                projectRootDirectory,
                            ),
                        requiresSecureMemorySupport =
                            DistributionContractReader.requiresSecureMemorySupport(
                                projectRootDirectory,
                            ),
                    ),
                bootstrap =
                    BootstrapDocument(
                        recommendedFirstCommand =
                            listOf(
                                bundleTarget.launcherCommand,
                                DistributionContractReader.helpOperationName(projectRootDirectory),
                            ),
                        machineReadableContractCommand =
                            listOf(
                                bundleTarget.launcherCommand,
                                DistributionContractReader.capabilitiesOperationName(projectRootDirectory),
                            ),
                        requestTemplateCommand =
                            listOf(
                                bundleTarget.launcherCommand,
                                DistributionContractReader.requestTemplateOperationName(projectRootDirectory),
                            ),
                        planTemplateCommand =
                            listOf(
                                bundleTarget.launcherCommand,
                                DistributionContractReader.planTemplateOperationName(projectRootDirectory),
                            ),
                    ),
                documentationFiles = bundleStagingLayout.documentationFiles,
            )
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document) +
            System.lineSeparator()
    }

    private data class BundleManifestDocument(
        val application: String,
        val version: String,
        val normalizedArtifactTimestampUtc: String,
        val artifactType: String,
        val archiveFormat: String,
        val runtimeDistribution: String,
        val publicCliDistribution: String,
        val bundleTarget: BundleTargetDocument,
        val supportedPublicCliBundleTargets: List<String>,
        val unsupportedPublicCliBundleTargets: List<String>,
        val launcher: String,
        val noExternalJavaRequired: Boolean,
        val requiresFingrindSqliteLibraryEnvironmentVariable: Boolean,
        val managedSqlite: ManagedSqliteDocument,
        val bootstrap: BootstrapDocument,
        val documentationFiles: List<String>,
    )

    private data class BundleTargetDocument(
        val classifier: String,
        val operatingSystem: String,
        val architecture: String,
        val compatibilityLabel: String,
        val minimumGlibcVersion: String?,
    )

    private data class ManagedSqliteDocument(
        val storageDriver: String,
        val storageEngine: String,
        val bookProtectionMode: String,
        val defaultBookCipher: String,
        val libraryMode: String,
        val requiredMinimumSqliteVersion: String,
        val requiredSqlite3mcVersion: String,
        val requiredSqliteSourceId: String,
        val requiredCompileOptions: List<String>,
        val forbiddenCompileOptions: List<String>,
        val requiresSecureMemorySupport: Boolean,
    )

    private data class BootstrapDocument(
        val recommendedFirstCommand: List<String>,
        val machineReadableContractCommand: List<String>,
        val requestTemplateCommand: List<String>,
        val planTemplateCommand: List<String>,
    )
}
