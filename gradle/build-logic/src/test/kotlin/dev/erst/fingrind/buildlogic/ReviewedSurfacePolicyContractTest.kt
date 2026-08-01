package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@Suppress("DEPRECATION")
class ReviewedSurfacePolicyContractTest {
    private val objectMapper = JsonMapper.builder().build()
    private val repositoryRoot = Path.of("").toAbsolutePath().normalize().parent.parent
    private val contractDirectory =
        repositoryRoot.resolve("scripts/structural_governance/reviewed_surface_policy_contract")

    @Test
    fun reviewedSurfacePolicyContract_casesStayAlignedWithKotlinWaiverSemantics() {
        val profiles = profilesByDocumentType()
        contractCases().forEach { case ->
            when (case.path("caseType").requiredText()) {
                "definition" -> assertDefinitionCase(case, profiles)
                "runtime" -> assertRuntimeCase(case, profiles)
                "orphan" -> assertOrphanCase(case, profiles)
                else ->
                    error(
                        "Unsupported reviewed-surface policy contract case type ${case.path("caseType").requiredText()}."
                    )
            }
        }
    }

    private fun contractDocuments(): List<JsonNode> =
        contractDirectory
            .toFile()
            .listFiles { file -> file.isFile && file.extension == "json" }
            ?.sortedBy { it.name }
            ?.map(objectMapper::readTree)
            ?: error("No reviewed-surface policy contract cases found in $contractDirectory")

    private fun contractCases(): List<JsonNode> =
        contractDocuments().filter { it.path("documentType").requiredText() == "case" }

    private fun profilesByDocumentType(): Map<String, Map<String, JsonNode>> =
        contractDocuments()
            .filter { it.path("documentType").requiredText() != "case" }
            .associate { document ->
                document.path("documentType").requiredText() to
                    document
                        .path("profiles")
                        .properties()
                        .asSequence()
                        .associate { entry -> entry.key to entry.value }
            }

    private fun profile(
        profiles: Map<String, Map<String, JsonNode>>,
        documentType: String,
        profileName: String,
    ): JsonNode =
        profiles[documentType]?.get(profileName)
            ?: error("Missing reviewed-surface policy contract profile $documentType/$profileName")

    private fun assertDefinitionCase(
        case: JsonNode,
        profiles: Map<String, Map<String, JsonNode>>,
    ) {
        val actual =
            reviewedSurfaceDefinitionViolations(
                reviewedSurface = reviewedSurface(case, profiles),
                defaultBudget = defaultBudget(case, profiles),
            ).map(::normalizeViolation)
        assertEquals(
            expectedDescriptors(case),
            actual,
            "Definition case ${case.path("id").requiredText()} drifted from the shared reviewed-surface policy contract.",
        )
    }

    private fun assertRuntimeCase(
        case: JsonNode,
        profiles: Map<String, Map<String, JsonNode>>,
    ) {
        val actual =
            reviewedSurfaceViolations(
                relativePath = case.path("relativePath").requiredText(),
                metrics = liveMetrics(case, profiles),
                reviewedSurface = reviewedSurface(case, profiles),
                defaultBudget = defaultBudget(case, profiles),
                currentDate = LocalDate.parse(case.path("currentDate").requiredText()),
            ).map(::normalizeViolation)
        assertEquals(
            expectedDescriptors(case),
            actual,
            "Runtime case ${case.path("id").requiredText()} drifted from the shared reviewed-surface policy contract.",
        )
    }

    private fun assertOrphanCase(
        case: JsonNode,
        profiles: Map<String, Map<String, JsonNode>>,
    ) {
        val reviewedSurface = reviewedSurface(case, profiles)
        val actual =
            missingReviewedSurfaceViolations(
                reviewedSurfaces = listOf(reviewedSurface),
                projectScope = "shared-policy-contract",
                existingRelativePaths = textArray(case.path("existingRelativePaths")).toSet(),
            ).map(::normalizeViolation)
        assertEquals(
            expectedDescriptors(case),
            actual,
            "Orphan case ${case.path("id").requiredText()} drifted from the shared reviewed-surface policy contract.",
        )
    }

    private fun expectedDescriptors(case: JsonNode): List<String> = textArray(case.path("expectedDescriptors"))

    private fun textArray(node: JsonNode): List<String> =
        (0 until node.size()).map { index -> node.get(index).requiredText() }

    private fun reviewedSurface(
        case: JsonNode,
        profiles: Map<String, Map<String, JsonNode>>,
    ): ReviewedJavaSourceSurface {
        val reviewedSurfaceProfile =
            profile(
                profiles,
                "reviewed-surface-profiles",
                case.path("reviewedSurfaceProfile").requiredText(),
            )
        val approvalProfile =
            profile(
                profiles,
                "approval-profiles",
                case.path("approvalProfile").requiredText(),
            )
        val budgetVarianceReason =
            if (case.has("budgetVarianceReason")) {
                case.path("budgetVarianceReason").takeUnless(JsonNode::isNull)?.requiredText()
            } else {
                reviewedSurfaceProfile
                    .path("budgetVarianceReason")
                    .takeUnless(JsonNode::isNull)
                    ?.requiredText()
            }
        return reviewedJavaSourceSurface(
            projectPath = FinGrindProjectPaths.CONTRACT,
            relativePath = case.path("relativePath").requiredText(),
            owner = reviewedSurfaceProfile.path("owner").requiredText(),
            reason = "Shared reviewed-surface policy contract case.",
            splitTrigger = reviewedSurfaceProfile.path("splitTrigger").requiredText(),
            roleName = reviewedSurfaceProfile.path("reviewedRoleName").requiredText(),
            budgetVarianceReason = budgetVarianceReason,
            approval = approval(approvalProfile),
        )
    }

    private fun defaultBudget(
        case: JsonNode,
        profiles: Map<String, Map<String, JsonNode>>,
    ): JavaSourceShapeBudget {
        val node =
            profile(
                profiles,
                "budget-profiles",
                case.path("defaultBudgetProfile").requiredText(),
            )
        return JavaSourceShapeBudget(
            roleName = node.path("roleName").requiredText(),
            maxPhysicalLines = node.path("physicalLines").asInt(),
            maxLogicalLines = node.path("logicalLines").asInt(),
            maxImports = node.path("importLikeLines").asInt(),
            maxNestedTypes = node.path("nestedTypes").asInt(),
            maxMethodsPerTopLevelType = node.path("functions").asInt(),
            maxFieldsPerTopLevelType = 1,
            maxSwitchArmsPerMethod = 1,
            maxMethodLineSpan = 10,
            maxMethodParameters = 2,
            maxMethodDecisionPoints = 2,
        )
    }

    private fun approval(node: JsonNode): ReviewedJavaSourceApproval =
        reviewedApproval(
            physicalLines = node.path("physicalLines").asInt(),
            logicalLines = node.path("logicalLines").asInt(),
            imports = node.path("importLikeLines").asInt(),
            nestedTypes = node.path("nestedTypes").asInt(),
            methodsPerTopLevelType = node.path("functions").asInt(),
            fieldsPerTopLevelType = 1,
            switchArmsPerMethod = 1,
            methodLineSpan = 10,
            methodParameters = 2,
            methodDecisionPoints = 2,
            expiresOn = LocalDate.parse(node.path("expiresOn").requiredText()),
        )

    private fun liveMetrics(
        case: JsonNode,
        profiles: Map<String, Map<String, JsonNode>>,
    ): JavaSourceShapeMetrics {
        val node =
            profile(
                profiles,
                "metrics-profiles",
                case.path("liveMetricsProfile").requiredText(),
            )
        return JavaSourceShapeMetrics(
            physicalLineCount = node.path("physicalLines").asInt(),
            logicalLineCount = node.path("logicalLines").asInt(),
            importCount = node.path("importLikeLines").asInt(),
            nestedTypeCount = node.path("nestedTypes").asInt(),
            maxMethodsPerTopLevelType = node.path("functions").asInt(),
            maxFieldsPerTopLevelType = 1,
            maxSwitchArmsPerMethod = 1,
            maxMethodLineSpan = 10,
            maxMethodParameters = 2,
            maxMethodDecisionPoints = 2,
        )
    }

    private fun normalizeViolation(violation: String): String =
        when {
            "without an explicit variance reason" in violation -> "variance-reason-required"
            "is no longer needed because the file fits the" in violation -> "waiver-unnecessary"
            "no longer resolves inside" in violation -> "orphaned-waiver"
            "expired on " in violation ->
                "waiver-expired:${Regex("""expired on (\d{4}-\d{2}-\d{2})""").find(violation)?.groupValues?.get(1)}"
            "no longer matches the live file on " in violation -> normalizeDriftViolation(violation)
            else -> error("Unrecognized reviewed-surface violation: $violation")
        }

    private fun normalizeDriftViolation(violation: String): String {
        val match =
            Regex("""live file on (.+?) \(approved (\d+), live (\d+)\)""")
                .find(violation)
                ?: error("Unrecognized drift violation: $violation")
        val dimension =
            when (match.groupValues[1]) {
                "physical lines" -> "physical-lines"
                "logical lines" -> "logical-lines"
                "imports" -> "imports"
                "nested types" -> "nested-types"
                "methods on one top-level type" -> "functions"
                else -> error("Unsupported shared drift dimension ${match.groupValues[1]}")
            }
        return "snapshot-drift:$dimension:${match.groupValues[2]}:${match.groupValues[3]}"
    }

    private fun JsonNode.requiredText(): String =
        textValue() ?: error("Expected text node in reviewed-surface policy contract: $this")
}
