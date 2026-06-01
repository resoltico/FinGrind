package dev.erst.fingrind.buildlogic

import java.nio.file.Path

/** Reads bundle-target and host-platform distribution contract facts. */
object DistributionBundleTargetReader {
    fun operatingSystemId(osName: String = System.getProperty("os.name", "")): String =
        DistributionHostPlatform.operatingSystemId(osName)

    fun architectureId(architecture: String = System.getProperty("os.arch", "unknown")): String =
        DistributionHostPlatform.architectureId(architecture)

    fun hostBundleTarget(
        projectRootDirectory: Path,
        osName: String = System.getProperty("os.name", ""),
        architecture: String = System.getProperty("os.arch", "unknown"),
    ): DistributionContractReader.BundleTargetContract =
        bundleTarget(
            projectRootDirectory,
            operatingSystemId(osName) + "-" + architectureId(architecture),
        )

    fun hostClassifier(
        projectRootDirectory: Path,
        osName: String = System.getProperty("os.name", ""),
        architecture: String = System.getProperty("os.arch", "unknown"),
    ): String = hostBundleTarget(projectRootDirectory, osName, architecture).classifier

    fun bundleTarget(
        projectRootDirectory: Path,
        classifier: String,
    ): DistributionContractReader.BundleTargetContract {
        val normalizedClassifier = classifier.trim()
        if (normalizedClassifier.isEmpty()) {
            throw IllegalStateException("Bundle target classifier must not be blank.")
        }
        return DistributionContractModels.bundleLayoutContract(projectRootDirectory)
            .bundleTargets[normalizedClassifier]
            ?: throw IllegalStateException(
                "Bundle target $normalizedClassifier is not declared in ${DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH}.",
            )
    }
}
