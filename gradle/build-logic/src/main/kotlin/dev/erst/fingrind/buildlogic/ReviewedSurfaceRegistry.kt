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
    private const val REGISTRY_DIRECTORY_RELATIVE_PATH =
        "scripts/structural_governance/reviewed_surface_registry"

    private val objectMapper = JsonMapper.builder().build()
    private val javaSurfaceCache = ConcurrentHashMap<Path, List<ReviewedJavaSourceSurface>>()
    private val javaSurfaceMapCache =
        ConcurrentHashMap<Path, Map<JavaReviewedSurfaceKey, ReviewedJavaSourceSurface>>()

    fun javaReviewedSurfaces(projectRootDirectory: Path): List<ReviewedJavaSourceSurface> {
        val registryRoot = registryRoot(projectRootDirectory)
        return javaSurfaceCache.computeIfAbsent(registryRoot, ::loadJavaReviewedSurfaces)
    }

    fun javaReviewedSurfaceMap(
        projectRootDirectory: Path,
    ): Map<JavaReviewedSurfaceKey, ReviewedJavaSourceSurface> {
        val registryRoot = registryRoot(projectRootDirectory)
        return javaSurfaceMapCache.computeIfAbsent(registryRoot) {
            javaReviewedSurfaces(projectRootDirectory).associateBy { reviewedSurface ->
                JavaReviewedSurfaceKey(
                    projectPath = reviewedSurface.projectPath,
                    relativePath = reviewedSurface.relativePath,
                )
            }
        }
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
        loadRegistryDocuments(registryRoot.resolve("text"), "text")
        val surfaces = mutableListOf<ReviewedJavaSourceSurface>()
        val seen = mutableMapOf<JavaReviewedSurfaceKey, Path>()
        for ((fragmentPath, node) in javaDocuments) {
            val reviewedSurface = javaReviewedSurface(node, fragmentPath)
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
                    "Duplicate Java reviewed-surface entries in ${pathText(duplicatePath)} and ${pathText(fragmentPath)} for ${key.projectPath}/${key.relativePath}.",
                )
            }
            surfaces += reviewedSurface
        }
        return surfaces
    }

    private fun loadRegistryDocuments(
        categoryDirectory: Path,
        categoryName: String,
    ): List<Pair<Path, JsonNode>> {
        if (!Files.isDirectory(categoryDirectory)) {
            throw IllegalStateException(
                "Reviewed-surface $categoryName registry directory ${pathText(categoryDirectory)} is missing.",
            )
        }
        val fragmentPaths =
            Files.walk(categoryDirectory).use { stream ->
                stream
                    .filter { candidate ->
                        Files.isRegularFile(candidate) &&
                            candidate.fileName.toString().endsWith(".json")
                    }.toList()
                    .sortedBy(::pathText)
            }
        if (fragmentPaths.isEmpty()) {
            throw IllegalStateException(
                "Reviewed-surface $categoryName registry directory ${pathText(categoryDirectory)} must contain at least one JSON fragment.",
            )
        }
        return fragmentPaths.map { fragmentPath ->
            fragmentPath to loadRegistryDocument(fragmentPath)
        }
    }

    private fun loadRegistryDocument(fragmentPath: Path): JsonNode =
        Files.newInputStream(fragmentPath).use { stream ->
            val document = objectMapper.readTree(stream)
            if (document == null || !document.isObject) {
                throw IllegalStateException(
                    "Reviewed-surface registry fragment ${pathText(fragmentPath)} must contain one top-level JSON object.",
                )
            }
            document
        }

    private fun javaReviewedSurface(
        node: JsonNode,
        fragmentPath: Path,
    ): ReviewedJavaSourceSurface {
        val approvalNode = requiredObject(node, "approval", fragmentPath)
        return ReviewedJavaSourceSurface(
            projectPath = requiredText(node, "projectPath", fragmentPath),
            relativePath = requiredText(node, "relativePath", fragmentPath),
            owner = requiredText(node, "owner", fragmentPath),
            reason = requiredText(node, "reason", fragmentPath),
            splitTrigger = requiredText(node, "splitTrigger", fragmentPath),
            reviewedRoleName = requiredText(node, "reviewedRoleName", fragmentPath),
            budgetVarianceReason = optionalText(node.path("budgetVarianceReason"), fragmentPath, "budgetVarianceReason"),
            duplicationExemptionReason =
                optionalText(
                    node.path("duplicationExemptionReason"),
                    fragmentPath,
                    "duplicationExemptionReason",
                ),
            approval =
                reviewedApproval(
                    physicalLines = requiredInt(approvalNode, "physicalLines", fragmentPath),
                    logicalLines = requiredInt(approvalNode, "logicalLines", fragmentPath),
                    imports = requiredInt(approvalNode, "importLikeLines", fragmentPath),
                    nestedTypes = requiredInt(approvalNode, "nestedTypes", fragmentPath),
                    methodsPerTopLevelType =
                        requiredInt(approvalNode, "functions", fragmentPath),
                    fieldsPerTopLevelType =
                        requiredInt(approvalNode, "fieldsPerTopLevelType", fragmentPath),
                    switchArmsPerMethod =
                        requiredInt(approvalNode, "switchArmsPerMethod", fragmentPath),
                    methodLineSpan =
                        requiredInt(approvalNode, "methodLineSpan", fragmentPath),
                    methodParameters =
                        requiredInt(approvalNode, "methodParameters", fragmentPath),
                    methodDecisionPoints =
                        requiredInt(approvalNode, "methodDecisionPoints", fragmentPath),
                    expiresOn =
                        LocalDate.parse(
                            requiredText(approvalNode, "expiresOn", fragmentPath),
                        ),
                ),
        )
    }

    private fun requiredObject(
        document: JsonNode,
        key: String,
        fragmentPath: Path,
    ): JsonNode {
        val node = document.path(key)
        if (!node.isObject) {
            throw IllegalStateException(
                "Reviewed-surface registry fragment ${pathText(fragmentPath)} must declare $key as one JSON object.",
            )
        }
        return node
    }

    private fun requiredText(
        document: JsonNode,
        key: String,
        fragmentPath: Path,
    ): String {
        val value = document.path(key).stringValue()?.trim().orEmpty()
        if (value.isEmpty()) {
            throw IllegalStateException(
                "Reviewed-surface registry fragment ${pathText(fragmentPath)} must declare $key as one non-blank string.",
            )
        }
        return value
    }

    private fun optionalText(
        node: JsonNode,
        fragmentPath: Path,
        key: String,
    ): String? {
        if (node.isMissingNode || node.isNull) {
            return null
        }
        val value = node.stringValue()?.trim().orEmpty()
        if (value.isEmpty()) {
            throw IllegalStateException(
                "Reviewed-surface registry fragment ${pathText(fragmentPath)} must declare $key as one non-blank string when present.",
            )
        }
        return value
    }

    private fun requiredInt(
        document: JsonNode,
        key: String,
        fragmentPath: Path,
    ): Int {
        val node = document.path(key)
        if (!node.isInt) {
            throw IllegalStateException(
                "Reviewed-surface registry fragment ${pathText(fragmentPath)} must declare approval.$key as one integer.",
            )
        }
        return node.intValue()
    }

    private fun pathText(path: Path): String = path.toString().replace('\\', '/')
}
