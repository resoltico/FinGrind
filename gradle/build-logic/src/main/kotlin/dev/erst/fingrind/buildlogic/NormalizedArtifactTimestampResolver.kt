package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import java.time.Instant

/** Resolves one reproducible but operator-meaningful timestamp for public bundle artifacts. */
object NormalizedArtifactTimestampResolver {
    private const val SOURCE_DATE_EPOCH = "SOURCE_DATE_EPOCH"

    fun resolve(projectRootDirectory: Path): Instant =
        Instant.ofEpochSecond(resolveEpochSeconds(projectRootDirectory))

    fun resolveEpochSeconds(
        projectRootDirectory: Path,
        environment: Map<String, String> = System.getenv(),
    ): Long {
        environment[SOURCE_DATE_EPOCH]?.let { return parseEpochSeconds(it, SOURCE_DATE_EPOCH) }
        return FinGrindBuildMetadata.load(projectRootDirectory).normalizedArtifactEpochSeconds
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
}
