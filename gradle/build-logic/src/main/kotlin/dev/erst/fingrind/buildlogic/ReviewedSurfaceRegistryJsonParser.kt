package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

internal object ReviewedSurfaceRegistryJsonParser {
    private val objectMapper = JsonMapper.builder().build()

    fun loadRegistryDocument(fragmentPath: Path): JsonNode =
        Files.newInputStream(fragmentPath).use { stream ->
            val document = objectMapper.readTree(stream)
            if (document == null || !document.isObject) {
                throw IllegalStateException(
                    "Reviewed-surface registry fragment ${reviewedSurfaceRegistryPathText(fragmentPath)} must contain one top-level JSON object.",
                )
            }
            document
        }

    fun reviewedJavaSourceSurface(
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
            budgetVarianceReason =
                optionalText(node.path("budgetVarianceReason"), fragmentPath, "budgetVarianceReason"),
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
                "Reviewed-surface registry fragment ${reviewedSurfaceRegistryPathText(fragmentPath)} must declare $key as one JSON object.",
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
                "Reviewed-surface registry fragment ${reviewedSurfaceRegistryPathText(fragmentPath)} must declare $key as one non-blank string.",
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
                "Reviewed-surface registry fragment ${reviewedSurfaceRegistryPathText(fragmentPath)} must declare $key as one non-blank string when present.",
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
                "Reviewed-surface registry fragment ${reviewedSurfaceRegistryPathText(fragmentPath)} must declare approval.$key as one integer.",
            )
        }
        return node.intValue()
    }
}
