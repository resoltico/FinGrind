package dev.erst.fingrind.buildlogic

import java.nio.file.Path

/**
 * Target-derived names and paths for one self-contained CLI bundle.
 *
 * The layout deliberately describes only the portable archive contract. It never selects a host
 * toolchain or produces an archive, so it can be proven for a Windows target on another host.
 */
internal object BundleStagingLayout {
    private const val ARCHIVE_ARTIFACT_PREFIX = "fingrind"
    private const val BUNDLE_SOURCE_ROOT = "src/bundle"
    private const val APPLICATION_JAR_PATH = "lib/app/fingrind.jar"
    private const val NATIVE_FORMAT_BOUNDARY_PROBE_PATH =
        "lib/release-smoke/native-sqlite-format-boundary-probe.jar"
    private const val RUNTIME_DIRECTORY_PATH = "runtime"
    private const val RUNTIME_RELEASE_PATH = "$RUNTIME_DIRECTORY_PATH/release"
    private const val RUNTIME_LEGAL_INDEX_PATH = "$RUNTIME_DIRECTORY_PATH/legal/INDEX.sha256"
    private const val RUNTIME_SOURCE_JDK_RELEASE_PATH =
        "$RUNTIME_DIRECTORY_PATH/provenance/source-jdk-release"
    private const val RUNTIME_REQUESTED_MODULES_PATH =
        "$RUNTIME_DIRECTORY_PATH/provenance/requested-modules.txt"
    private const val NATIVE_DIRECTORY_PATH = "lib/native"
    private const val BUNDLE_MANIFEST_PATH = "bundle-manifest.json"
    private const val TOOLCHAIN_FINGERPRINT_PATH = "lib/native/toolchain-fingerprint.json"
    private const val NATIVE_BUILD_CONTRACT_PATH = "lib/native/build-contract.json"

    private val rootTemplateSourceIncludePaths =
        listOf(
            "README.md",
            "quick-start-request.json",
        )

    private val legalDocumentPaths =
        listOf(
            "LICENSE",
            "LICENSE-APACHE-2.0",
            "LICENSE-CC0-1.0",
            "LICENSE-SIL-OFL-1.1",
            "LICENSE-SQLITE3MULTIPLECIPHERS",
            "LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY",
            "NOTICE",
            "NOTICE-ZULU-26.32.203",
            "PATENTS.md",
            "SOURCE_OFFER.md",
        )

    fun bundleName(version: String, classifier: String): String {
        val checkedVersion =
            WindowsPortableArchivePathPolicy.requireComponent(
                BundleStagingContractValidation.requireNonBlank(version, "bundle version"),
                "bundle version",
            )
        val checkedClassifier =
            WindowsPortableArchivePathPolicy.requireComponent(
                BundleStagingContractValidation.requireNonBlank(classifier, "bundle classifier"),
                "bundle classifier",
            )
        return WindowsPortableArchivePathPolicy.requireComponent(
            "$ARCHIVE_ARTIFACT_PREFIX-$checkedVersion-$checkedClassifier",
            "bundle root directory name",
        )
    }

    fun plan(
        projectRootDirectory: Path,
        version: String,
        classifier: String,
    ): BundleStagingPlan =
        plan(
            version = version,
            bundleTarget =
                DistributionBundleTargetReader.bundleTarget(projectRootDirectory, classifier),
        )

    fun plan(
        version: String,
        bundleTarget: BundleTargetContract,
    ): BundleStagingPlan {
        val launcherPath =
            WindowsPortableArchivePathPolicy.requireRelativeArchivePath(
                bundleTarget.launcherPath,
                "bundle launcher path",
            )
        val nativeLibraryFileName =
            WindowsPortableArchivePathPolicy.requireFileName(
                bundleTarget.sqliteLibraryFileName,
                "SQLite library file name",
            )
        val archiveFormat =
            BundleStagingContractValidation.requireSupportedArchiveFormat(bundleTarget.archiveFormat)
        val bundleName = bundleName(version, bundleTarget.classifier)
        val archiveFileName =
            WindowsPortableArchivePathPolicy.requireComponent(
                "$bundleName.$archiveFormat",
                "bundle archive file name",
            )
        val runtimeJavaExecutableName =
            BundleStagingContractValidation.runtimeJavaExecutableName(bundleTarget.operatingSystemId)
        val runtimeJavaPath =
            "$RUNTIME_DIRECTORY_PATH/bin/$runtimeJavaExecutableName"
        val nativeLibraryPath = "$NATIVE_DIRECTORY_PATH/$nativeLibraryFileName"
        val nativeLibraryChecksumPath = "$nativeLibraryPath.sha256"
        val documentationFiles =
            rootTemplateSourceIncludePaths + BUNDLE_MANIFEST_PATH + legalDocumentPaths
        val requiredArchiveFilePaths =
            listOf(
                launcherPath,
                APPLICATION_JAR_PATH,
                NATIVE_FORMAT_BOUNDARY_PROBE_PATH,
                runtimeJavaPath,
                RUNTIME_RELEASE_PATH,
                RUNTIME_LEGAL_INDEX_PATH,
                RUNTIME_SOURCE_JDK_RELEASE_PATH,
                RUNTIME_REQUESTED_MODULES_PATH,
                nativeLibraryPath,
                nativeLibraryChecksumPath,
                TOOLCHAIN_FINGERPRINT_PATH,
                NATIVE_BUILD_CONTRACT_PATH,
            ) + documentationFiles
        WindowsPortableArchivePathPolicy.requireNoCaseInsensitiveArchivePathCollisions(
            requiredArchiveFilePaths,
            "Bundle staging layout for ${bundleTarget.classifier}",
        )

        return BundleStagingPlan(
            bundleTarget = bundleTarget,
            bundleName = bundleName,
            archiveFormat = archiveFormat,
            archiveFileName = archiveFileName,
            launcherPath = launcherPath,
            launcherTemplateSourcePath = "$BUNDLE_SOURCE_ROOT/$launcherPath",
            launcherSourceIncludePaths = listOf(launcherPath),
            rootTemplateSourceIncludePaths = rootTemplateSourceIncludePaths,
            applicationJarPath = APPLICATION_JAR_PATH,
            nativeFormatBoundaryProbePath = NATIVE_FORMAT_BOUNDARY_PROBE_PATH,
            runtimeDirectoryPath = RUNTIME_DIRECTORY_PATH,
            runtimeJavaPath = runtimeJavaPath,
            runtimeReleasePath = RUNTIME_RELEASE_PATH,
            runtimeLegalIndexPath = RUNTIME_LEGAL_INDEX_PATH,
            runtimeSourceJdkReleasePath = RUNTIME_SOURCE_JDK_RELEASE_PATH,
            runtimeRequestedModulesPath = RUNTIME_REQUESTED_MODULES_PATH,
            nativeDirectoryPath = NATIVE_DIRECTORY_PATH,
            nativeLibraryFileName = nativeLibraryFileName,
            nativeLibraryPath = nativeLibraryPath,
            nativeLibraryChecksumPath = nativeLibraryChecksumPath,
            toolchainFingerprintPath = TOOLCHAIN_FINGERPRINT_PATH,
            nativeBuildContractPath = NATIVE_BUILD_CONTRACT_PATH,
            bundleManifestPath = BUNDLE_MANIFEST_PATH,
            legalDocumentPaths = legalDocumentPaths,
            documentationFiles = documentationFiles,
            requiredArchiveFilePaths = requiredArchiveFilePaths,
        )
    }
}

internal data class BundleStagingPlan(
    val bundleTarget: BundleTargetContract,
    val bundleName: String,
    val archiveFormat: String,
    val archiveFileName: String,
    val launcherPath: String,
    val launcherTemplateSourcePath: String,
    val launcherSourceIncludePaths: List<String>,
    val rootTemplateSourceIncludePaths: List<String>,
    val applicationJarPath: String,
    val nativeFormatBoundaryProbePath: String,
    val runtimeDirectoryPath: String,
    val runtimeJavaPath: String,
    val runtimeReleasePath: String,
    val runtimeLegalIndexPath: String,
    val runtimeSourceJdkReleasePath: String,
    val runtimeRequestedModulesPath: String,
    val nativeDirectoryPath: String,
    val nativeLibraryFileName: String,
    val nativeLibraryPath: String,
    val nativeLibraryChecksumPath: String,
    val toolchainFingerprintPath: String,
    val nativeBuildContractPath: String,
    val bundleManifestPath: String,
    val legalDocumentPaths: List<String>,
    val documentationFiles: List<String>,
    val requiredArchiveFilePaths: List<String>,
) {
    fun targetTemplateProperties(): Map<String, String> =
        mapOf(
            "bundleArchiveFormat" to archiveFormat,
            "bundleClassifier" to bundleTarget.classifier,
            "bundleOperatingSystem" to bundleTarget.operatingSystemId,
            "bundleArchitecture" to bundleTarget.architectureId,
            "bundleLauncherPath" to launcherPath,
            "bundleLauncherCommand" to bundleTarget.launcherCommand,
        )

    fun launcherTemplateProperties(
        bundleRuntimeDistribution: String,
        sqliteBundleHomeSystemProperty: String,
    ): Map<String, String> =
        mapOf(
            "bundleRuntimeDistribution" to bundleRuntimeDistribution,
            "sqliteBundleHomeSystemProperty" to sqliteBundleHomeSystemProperty,
        )
}
