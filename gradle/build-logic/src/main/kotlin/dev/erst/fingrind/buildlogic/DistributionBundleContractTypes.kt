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
) {
    init {
        WindowsPortableArchivePathPolicy.requireComponent(classifier, "bundle target classifier")
        WindowsPortableArchivePathPolicy.requireRelativeArchivePath(launcherPath, "bundle launcher path")
        WindowsPortableArchivePathPolicy.requireFileName(
            sqliteLibraryFileName,
            "SQLite library file name",
        )
    }
}

data class PublicBundlePublicationContract(
    val status: String,
)

internal const val PUBLICATION_STATUS_PUBLISHED = "published"
internal const val PUBLICATION_STATUS_NOT_PUBLISHED = "not-published"
