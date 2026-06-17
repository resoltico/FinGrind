package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import tools.jackson.databind.JsonNode

internal data class JavaReviewedSurfaceKey(
    val projectPath: String,
    val relativePath: String,
)

internal data class JavaReviewedSurfaceRegistrySnapshot(
    val surfaces: List<ReviewedJavaSourceSurface>,
    val surfaceMap: Map<JavaReviewedSurfaceKey, ReviewedJavaSourceSurface>,
)

internal object ReviewedSurfaceRegistry {
    private const val REGISTRY_DIRECTORY_RELATIVE_PATH =
        "scripts/structural_governance/reviewed_surface_registry"

    fun registryFragmentFiles(projectRootDirectory: Path): List<Path> {
        val registryRoot = registryRoot(projectRootDirectory)
        return (
            registryFragmentPaths(registryRoot.resolve("java"), "java") +
                registryFragmentPaths(registryRoot.resolve("text"), "text")
        )
    }

    fun javaSnapshot(projectRootDirectory: Path): JavaReviewedSurfaceRegistrySnapshot {
        val registryRoot = registryRoot(projectRootDirectory)
        val surfaces = loadJavaReviewedSurfaces(registryRoot)
        return JavaReviewedSurfaceRegistrySnapshot(
            surfaces = surfaces,
            surfaceMap =
                surfaces.associateBy { reviewedSurface ->
                    JavaReviewedSurfaceKey(
                        projectPath = reviewedSurface.projectPath,
                        relativePath = reviewedSurface.relativePath,
                    )
                },
        )
    }

    fun javaReviewedSurfaces(projectRootDirectory: Path): List<ReviewedJavaSourceSurface> {
        return javaSnapshot(projectRootDirectory).surfaces
    }

    fun javaReviewedSurfaceMap(
        projectRootDirectory: Path,
    ): Map<JavaReviewedSurfaceKey, ReviewedJavaSourceSurface> {
        return javaSnapshot(projectRootDirectory).surfaceMap
    }

    private fun registryRoot(projectRootDirectory: Path): Path =
        sequenceOf(
                projectRootDirectory.resolve(REGISTRY_DIRECTORY_RELATIVE_PATH),
                projectRootDirectory.resolve("..").resolve(REGISTRY_DIRECTORY_RELATIVE_PATH),
            )
            .map(Path::normalize)
            .firstOrNull(Files::isDirectory)
            ?: throw IllegalStateException(
                "Missing reviewed-surface registry directory $REGISTRY_DIRECTORY_RELATIVE_PATH for $projectRootDirectory.",
            )

    private fun loadJavaReviewedSurfaces(registryRoot: Path): List<ReviewedJavaSourceSurface> {
        val javaDocuments = loadRegistryDocuments(registryRoot.resolve("java"), "java")
        registryFragmentPaths(registryRoot.resolve("text"), "text")
        val surfaces = mutableListOf<ReviewedJavaSourceSurface>()
        val seen = mutableMapOf<JavaReviewedSurfaceKey, Path>()
        for ((fragmentPath, node) in javaDocuments) {
            val reviewedSurface =
                ReviewedSurfaceRegistryJsonParser.reviewedJavaSourceSurface(node, fragmentPath)
            val key =
                JavaReviewedSurfaceKey(
                    projectPath = reviewedSurface.projectPath,
                    relativePath = reviewedSurface.relativePath,
                )
            val duplicatePath =
                seen.putIfAbsent(
                    key,
                    fragmentPath,
                )
            if (duplicatePath != null) {
                throw IllegalStateException(
                    "Duplicate Java reviewed-surface entries in ${reviewedSurfaceRegistryPathText(duplicatePath)} and ${reviewedSurfaceRegistryPathText(fragmentPath)} for ${key.projectPath}/${key.relativePath}.",
                )
            }
            surfaces += reviewedSurface
        }
        return surfaces
    }

    private fun loadRegistryDocuments(
        categoryDirectory: Path,
        categoryName: String,
    ): List<Pair<Path, JsonNode>> =
        registryFragmentPaths(categoryDirectory, categoryName).map { fragmentPath ->
            fragmentPath to ReviewedSurfaceRegistryJsonParser.loadRegistryDocument(fragmentPath)
        }

    private fun registryFragmentPaths(
        categoryDirectory: Path,
        categoryName: String,
    ): List<Path> {
        if (!Files.isDirectory(categoryDirectory)) {
            throw IllegalStateException(
                "Reviewed-surface $categoryName registry directory ${reviewedSurfaceRegistryPathText(categoryDirectory)} is missing.",
            )
        }
        val fragmentPaths =
            Files.walk(categoryDirectory).use { stream ->
                stream
                    .filter { candidate ->
                        Files.isRegularFile(candidate) &&
                            candidate.fileName.toString().endsWith(".json")
                    }.toList()
                    .sortedBy(::reviewedSurfaceRegistryPathText)
            }
        if (fragmentPaths.isEmpty()) {
            throw IllegalStateException(
                "Reviewed-surface $categoryName registry directory ${reviewedSurfaceRegistryPathText(categoryDirectory)} must contain at least one JSON fragment.",
            )
        }
        return fragmentPaths
    }
}

internal fun reviewedSurfaceRegistryPathText(path: Path): String = path.toString().replace('\\', '/')
