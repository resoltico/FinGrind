package dev.erst.fingrind.buildlogic

import java.nio.file.Path

/** Canonical template values shared by real staging and structural target-layout verification. */
internal object BundleStagingTemplateProperties {
    fun resolve(
        projectRootDirectory: Path,
        version: String,
        bundleStagingLayout: BundleStagingPlan,
    ): Map<String, String> =
        mapOf(
            "version" to version,
            "publicCliDistribution" to
                DistributionContractReader.publicCliDistribution(projectRootDirectory),
            "storageDriver" to DistributionContractReader.storageDriver(projectRootDirectory),
            "storageEngine" to DistributionContractReader.storageEngine(projectRootDirectory),
            "bookProtectionMode" to DistributionContractReader.bookProtectionMode(projectRootDirectory),
            "defaultBookCipher" to DistributionContractReader.defaultBookCipher(projectRootDirectory),
            "sqliteLibraryMode" to DistributionContractReader.sqliteLibraryMode(projectRootDirectory),
            "requiredMinimumSqliteVersion" to
                DistributionContractReader.requiredMinimumSqliteVersion(projectRootDirectory),
            "requiredSqlite3mcVersion" to
                DistributionContractReader.requiredSqlite3mcVersion(projectRootDirectory),
            "helpOperation" to DistributionContractReader.helpOperationName(projectRootDirectory),
            "capabilitiesOperation" to
                DistributionContractReader.capabilitiesOperationName(projectRootDirectory),
            "requestTemplateOperation" to
                DistributionContractReader.requestTemplateOperationName(projectRootDirectory),
            "planTemplateOperation" to
                DistributionContractReader.planTemplateOperationName(projectRootDirectory),
            "publicBundleTargetsMarkdown" to
                DistributionTextRendering.markdownBulletList(
                    DistributionContractReader.publicCliBundleTargets(projectRootDirectory),
                ),
            "unsupportedPublicBundleTargetsMarkdown" to
                DistributionTextRendering.markdownBulletList(
                    DistributionContractReader.unsupportedPublicCliBundleTargets(projectRootDirectory),
                ),
        ) +
            bundleStagingLayout.targetTemplateProperties() +
            bundleStagingLayout.launcherTemplateProperties(
                bundleRuntimeDistribution =
                    DistributionContractReader.bundleRuntimeDistribution(projectRootDirectory),
                sqliteBundleHomeSystemProperty =
                    DistributionContractReader.sqliteBundleHomeSystemProperty(projectRootDirectory),
            )
}
