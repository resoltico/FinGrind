package dev.erst.fingrind.buildlogic

internal data class BundleLayoutSchema(
    val bundleTargets: String,
    val operatingSystemId: String,
    val architectureId: String,
    val archiveFormat: String,
    val launcherPath: String,
    val launcherCommand: String,
    val sqliteLibraryFileName: String,
    val compatibilityLabel: String,
    val minimumGlibcVersion: String,
    val compatibilitySmokeContainerImage: String,
)

internal data class BundlePublicationSchema(
    val bundleTargets: String,
    val publicationStatus: String,
    val runnerLabel: String,
    val expectedRunnerOs: String,
    val expectedRunnerArch: String,
)

internal data class BundleLayoutContract(val bundleTargets: Map<String, BundleTargetContract>)

data class BundleTargetContract(
    val classifier: String,
    val operatingSystemId: String,
    val architectureId: String,
    val archiveFormat: String,
    val launcherPath: String,
    val launcherCommand: String,
    val sqliteLibraryFileName: String,
    val compatibilityLabel: String,
    val minimumGlibcVersion: String?,
    val compatibilitySmokeContainerImage: String?,
    val publicBundlePublication: PublicBundlePublicationContract,
)

data class PublicBundlePublicationContract(
    val status: String,
    val runnerLabel: String?,
    val expectedRunnerOs: String?,
    val expectedRunnerArch: String?,
)

internal const val PUBLICATION_STATUS_PUBLISHED = "published"
internal const val PUBLICATION_STATUS_NOT_PUBLISHED = "not-published"
