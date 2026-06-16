package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

internal data class JavaReviewedSurfaceKey(
    val projectPath: String,
    val relativePath: String,
)

internal object ReviewedSurfaceRegistry {
    private const val REGISTRY_RELATIVE_PATH =
        "scripts/structural_governance/reviewed_surface_registry.json"

    private val objectMapper = JsonMapper.builder().build()
    private val javaSurfaceCache = ConcurrentHashMap<Path, List<ReviewedJavaSourceSurface>>()
    private val javaSurfaceMapCache =
        ConcurrentHashMap<Path, Map<JavaReviewedSurfaceKey, ReviewedJavaSourceSurface>>()

    fun javaReviewedSurfaces(projectRootDirectory: Path): List<ReviewedJavaSourceSurface> {
        val registryPath = registryPath(projectRootDirectory)
        return javaSurfaceCache.computeIfAbsent(registryPath, ::loadJavaReviewedSurfaces)
    }

    fun javaReviewedSurfaceMap(
        projectRootDirectory: Path,
    ): Map<JavaReviewedSurfaceKey, ReviewedJavaSourceSurface> {
        val registryPath = registryPath(projectRootDirectory)
        return javaSurfaceMapCache.computeIfAbsent(registryPath) {
            javaReviewedSurfaces(projectRootDirectory).associateBy { reviewedSurface ->
                JavaReviewedSurfaceKey(
                    projectPath = reviewedSurface.projectPath,
                    relativePath = reviewedSurface.relativePath,
                )
            }
        }
    }

    private fun registryPath(projectRootDirectory: Path): Path =
        DistributionContractPaths.contractPath(projectRootDirectory, REGISTRY_RELATIVE_PATH).normalize()

    private fun loadJavaReviewedSurfaces(registryPath: Path): List<ReviewedJavaSourceSurface> {
        val document = loadRegistryDocument(registryPath)
        val surfaces =
            requiredArray(document, "javaReviewedSurfaces", registryPath)
                .mapIndexed { index, node -> javaReviewedSurface(node, registryPath, index) }
        val duplicateKeys =
            surfaces
                .groupBy { reviewedSurface ->
                    JavaReviewedSurfaceKey(
                        projectPath = reviewedSurface.projectPath,
                        relativePath = reviewedSurface.relativePath,
                    )
                }.filterValues { it.size > 1 }
                .keys
        if (duplicateKeys.isNotEmpty()) {
            throw IllegalStateException(
                "Duplicate Java reviewed-surface entries in ${pathText(registryPath)}: $duplicateKeys",
            )
        }
        return surfaces
    }

    private fun loadRegistryDocument(registryPath: Path): JsonNode =
        Files.newInputStream(registryPath).use { stream ->
            val document = objectMapper.readTree(stream)
            if (document == null || !document.isObject) {
                throw IllegalStateException(
                    "Reviewed-surface registry ${pathText(registryPath)} must contain one top-level JSON object.",
                )
            }
            requiredArray(document, "javaReviewedSurfaces", registryPath)
            requiredArray(document, "textReviewedSurfaces", registryPath)
            document
        }

    private fun javaReviewedSurface(
        node: JsonNode,
        registryPath: Path,
        index: Int,
    ): ReviewedJavaSourceSurface {
        val approvalNode = requiredObject(node, "approval", registryPath, index)
        return ReviewedJavaSourceSurface(
            projectPath = requiredText(node, "projectPath", registryPath, index),
            relativePath = requiredText(node, "relativePath", registryPath, index),
            owner = requiredText(node, "owner", registryPath, index),
            reason = requiredText(node, "reason", registryPath, index),
            splitTrigger = requiredText(node, "splitTrigger", registryPath, index),
            reviewedRoleName = requiredText(node, "reviewedRoleName", registryPath, index),
            budgetVarianceReason = optionalText(node.path("budgetVarianceReason"), registryPath, index, "budgetVarianceReason"),
            duplicationExemptionReason =
                optionalText(
                    node.path("duplicationExemptionReason"),
                    registryPath,
                    index,
                    "duplicationExemptionReason",
                ),
            approval =
                reviewedApproval(
                    physicalLines = requiredInt(approvalNode, "physicalLines", registryPath, index),
                    logicalLines = requiredInt(approvalNode, "logicalLines", registryPath, index),
                    imports = requiredInt(approvalNode, "importLikeLines", registryPath, index),
                    nestedTypes = requiredInt(approvalNode, "nestedTypes", registryPath, index),
                    methodsPerTopLevelType =
                        requiredInt(approvalNode, "functions", registryPath, index),
                    fieldsPerTopLevelType =
                        requiredInt(approvalNode, "fieldsPerTopLevelType", registryPath, index),
                    switchArmsPerMethod =
                        requiredInt(approvalNode, "switchArmsPerMethod", registryPath, index),
                    methodLineSpan =
                        requiredInt(approvalNode, "methodLineSpan", registryPath, index),
                    methodParameters =
                        requiredInt(approvalNode, "methodParameters", registryPath, index),
                    methodDecisionPoints =
                        requiredInt(approvalNode, "methodDecisionPoints", registryPath, index),
                    expiresOn =
                        LocalDate.parse(
                            requiredText(approvalNode, "expiresOn", registryPath, index),
                        ),
                ),
        )
    }

    private fun requiredArray(document: JsonNode, key: String, registryPath: Path): JsonNode {
        val node = document.path(key)
        if (!node.isArray) {
            throw IllegalStateException(
                "Reviewed-surface registry ${pathText(registryPath)} must declare $key as one JSON array.",
            )
        }
        return node
    }

    private fun requiredObject(
        document: JsonNode,
        key: String,
        registryPath: Path,
        index: Int,
    ): JsonNode {
        val node = document.path(key)
        if (!node.isObject) {
            throw IllegalStateException(
                "Reviewed-surface registry ${pathText(registryPath)} must declare javaReviewedSurfaces[$index].$key as one JSON object.",
            )
        }
        return node
    }

    private fun requiredText(
        document: JsonNode,
        key: String,
        registryPath: Path,
        index: Int,
    ): String {
        val value = document.path(key).stringValue()?.trim().orEmpty()
        if (value.isEmpty()) {
            throw IllegalStateException(
                "Reviewed-surface registry ${pathText(registryPath)} must declare javaReviewedSurfaces[$index].$key as one non-blank string.",
            )
        }
        return value
    }

    private fun optionalText(
        node: JsonNode,
        registryPath: Path,
        index: Int,
        key: String,
    ): String? {
        if (node.isMissingNode || node.isNull) {
            return null
        }
        val value = node.stringValue()?.trim().orEmpty()
        if (value.isEmpty()) {
            throw IllegalStateException(
                "Reviewed-surface registry ${pathText(registryPath)} must declare javaReviewedSurfaces[$index].$key as one non-blank string when present.",
            )
        }
        return value
    }

    private fun requiredInt(
        document: JsonNode,
        key: String,
        registryPath: Path,
        index: Int,
    ): Int {
        val node = document.path(key)
        if (!node.isInt) {
            throw IllegalStateException(
                "Reviewed-surface registry ${pathText(registryPath)} must declare javaReviewedSurfaces[$index].approval.$key as one integer.",
            )
        }
        return node.intValue()
    }

    private fun pathText(path: Path): String = path.toString().replace('\\', '/')
}
