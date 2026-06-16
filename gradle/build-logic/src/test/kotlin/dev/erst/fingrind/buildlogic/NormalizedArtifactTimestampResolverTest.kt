package dev.erst.fingrind.buildlogic

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NormalizedArtifactTimestampResolverTest {
    @Test
    fun sourceDateEpoch_overridesGitAndFallbackMetadata() {
        val repositoryRoot = Files.createTempDirectory("normalized-artifact-source-date-epoch")
        try {
            writeBuildMetadata(repositoryRoot, normalizedArtifactEpochSeconds = 1700000000L)

            val epochSeconds =
                NormalizedArtifactTimestampResolver.resolveEpochSeconds(
                    repositoryRoot,
                    environment = mapOf("SOURCE_DATE_EPOCH" to "1800000000"),
                )

            assertEquals(1800000000L, epochSeconds)
        } finally {
            DistributionContractReaderTestSupport.deleteTree(repositoryRoot)
        }
    }

    @Test
    fun fallbackMetadata_isUsedWhenEnvironmentIsUnset() {
        val repositoryRoot = Files.createTempDirectory("normalized-artifact-metadata-fallback")
        try {
            writeBuildMetadata(repositoryRoot, normalizedArtifactEpochSeconds = 1781455388L)

            val epochSeconds =
                NormalizedArtifactTimestampResolver.resolveEpochSeconds(
                    repositoryRoot,
                    environment = emptyMap(),
                )

            assertEquals(1781455388L, epochSeconds)
        } finally {
            DistributionContractReaderTestSupport.deleteTree(repositoryRoot)
        }
    }

    @Test
    fun invalidSourceDateEpoch_isRejected() {
        val repositoryRoot = Files.createTempDirectory("normalized-artifact-invalid-source-date")
        try {
            writeBuildMetadata(repositoryRoot, normalizedArtifactEpochSeconds = 1781455388L)

            assertFailsWith<IllegalArgumentException> {
                NormalizedArtifactTimestampResolver.resolveEpochSeconds(
                    repositoryRoot,
                    environment = mapOf("SOURCE_DATE_EPOCH" to "not-an-integer"),
                )
            }
        } finally {
            DistributionContractReaderTestSupport.deleteTree(repositoryRoot)
        }
    }

    @Test
    fun oddSourceDateEpoch_isRejected() {
        val repositoryRoot = Files.createTempDirectory("normalized-artifact-odd-source-date")
        try {
            writeBuildMetadata(repositoryRoot, normalizedArtifactEpochSeconds = 1781455388L)

            assertFailsWith<IllegalArgumentException> {
                NormalizedArtifactTimestampResolver.resolveEpochSeconds(
                    repositoryRoot,
                    environment = mapOf("SOURCE_DATE_EPOCH" to "1781455389"),
                )
            }
        } finally {
            DistributionContractReaderTestSupport.deleteTree(repositoryRoot)
        }
    }

    @Test
    fun oddFallbackMetadata_isRejected() {
        val repositoryRoot = Files.createTempDirectory("normalized-artifact-odd-metadata")
        try {
            writeBuildMetadata(repositoryRoot, normalizedArtifactEpochSeconds = 1781455389L)

            assertFailsWith<IllegalArgumentException> {
                NormalizedArtifactTimestampResolver.resolveEpochSeconds(
                    repositoryRoot,
                    environment = emptyMap(),
                )
            }
        } finally {
            DistributionContractReaderTestSupport.deleteTree(repositoryRoot)
        }
    }

    private fun writeBuildMetadata(projectRootDirectory: Path, normalizedArtifactEpochSeconds: Long) {
        val metadataPath = projectRootDirectory.resolve("gradle/fingrind-build.properties")
        Files.createDirectories(metadataPath.parent)
        Files.writeString(
            metadataPath,
            """
            fingrindJavaVersion=26
            fingrindPythonVersion=3.12
            fingrindKotlinVersion=2.4.0
            fingrindUvVersion=0.11.15
            implementationVendor=Ervins Strauhmanis
            implementationLicense=MIT
            foojayResolverConventionVersion=1.0.0
            normalizedArtifactEpochSeconds=$normalizedArtifactEpochSeconds
            """.trimIndent() + System.lineSeparator(),
            StandardCharsets.UTF_8,
        )
    }
}
