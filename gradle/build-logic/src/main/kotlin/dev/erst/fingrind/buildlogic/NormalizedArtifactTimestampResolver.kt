package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import java.time.Instant

/** Resolves one reproducible but operator-meaningful timestamp for public bundle artifacts. */
object NormalizedArtifactTimestampResolver {
    private const val SOURCE_DATE_EPOCH = "SOURCE_DATE_EPOCH"
    private const val ZIP_TIMESTAMP_GRANULARITY_SECONDS = 2L

    fun resolve(projectRootDirectory: Path): Instant =
        Instant.ofEpochSecond(resolveEpochSeconds(projectRootDirectory))

    fun resolveEpochSeconds(
        projectRootDirectory: Path,
        environment: Map<String, String> = System.getenv(),
    ): Long {
        environment[SOURCE_DATE_EPOCH]?.let {
            return requireZipPortableEpochSeconds(
                parseEpochSeconds(it, SOURCE_DATE_EPOCH),
                SOURCE_DATE_EPOCH,
            )
        }
        return requireZipPortableEpochSeconds(
            FinGrindBuildMetadata.load(projectRootDirectory).normalizedArtifactEpochSeconds,
            "normalizedArtifactEpochSeconds",
        )
    }

    private fun parseEpochSeconds(rawValue: String, sourceLabel: String): Long {
        val trimmed = rawValue.trim()
        require(trimmed.isNotEmpty()) { "$sourceLabel must not be blank." }
        val epochSeconds =
            trimmed.toLongOrNull()
                ?: throw IllegalArgumentException("$sourceLabel must be one integer epoch-seconds value.")
        require(epochSeconds > 0) { "$sourceLabel must be greater than zero." }
        return epochSeconds
    }

    private fun requireZipPortableEpochSeconds(epochSeconds: Long, sourceLabel: String): Long {
        require(epochSeconds % ZIP_TIMESTAMP_GRANULARITY_SECONDS == 0L) {
            "$sourceLabel must be divisible by 2 because ZIP bundle timestamps preserve even-second granularity only."
        }
        return epochSeconds
    }
}
