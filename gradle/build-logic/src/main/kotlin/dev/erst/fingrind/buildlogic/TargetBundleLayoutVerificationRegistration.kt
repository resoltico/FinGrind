package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import java.time.Instant
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.register

/** Registers the non-distributable target-layout proof at the distribution lifecycle seam. */
internal fun Project.registerTargetBundleLayoutVerification(
    repositoryRootDirectory: Path,
    bundleStagingLayout: BundleStagingPlan,
    buildMetadata: FinGrindBuildMetadata.Values,
    verificationBundleClassifier: Provider<String>,
    normalizedArtifactTimestamp: Provider<Instant>,
) {
    tasks.register<VerifyTargetBundleLayoutTask>("verifyTargetBundleLayout") {
        group = "verification"
        description =
            "Verifies a non-distributable synthetic bundle layout for one target through the canonical structural contract."
        contractFiles.from(DistributionContractReader.requiredContractFiles(repositoryRootDirectory))
        bundleSourceDirectory.set(layout.projectDirectory.dir("src/bundle"))
        legalDocumentFiles.from(bundleStagingLayout.legalDocumentPaths.map(rootProject::file))
        verifierScript.set(
            rootProject.layout.projectDirectory.file("scripts/verify-bundle-archive-contract.py"),
        )
        projectRootDirectoryPath.set(repositoryRootDirectory.toString())
        applicationName.set(rootProject.name)
        versionText.set(project.version.toString())
        bundleClassifier.set(verificationBundleClassifier)
        normalizedArtifactTimestampUtc.set(normalizedArtifactTimestamp.map(Instant::toString))
        pythonExecutable.set(
            providers.gradleProperty("fingrindPythonExecutable").orElse(
                "python${buildMetadata.pythonVersion}",
            ),
        )
        verificationReceiptFile.set(
            verificationBundleClassifier.flatMap { classifier ->
                layout.buildDirectory.file("verification/target-layout/$classifier.verified")
            },
        )
    }
}
