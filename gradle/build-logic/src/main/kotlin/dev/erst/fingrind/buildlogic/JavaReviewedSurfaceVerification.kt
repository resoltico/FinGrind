package dev.erst.fingrind.buildlogic

import java.time.LocalDate

internal fun reviewedSurfaceDefinitionViolations(
    reviewedSurface: ReviewedJavaSourceSurface,
    defaultBudget: JavaSourceShapeBudget,
): List<String> {
    val violations = mutableListOf<String>()
    if (
        approvalExceedsDefaultBudget(reviewedSurface.approval, defaultBudget) &&
        reviewedSurface.budgetVarianceReason == null
    ) {
        violations +=
            "${reviewedSurface.relativePath}: reviewed surface for ${reviewedSurface.owner} widens the ${defaultBudget.roleName} budget without an explicit variance reason."
    }
    return violations
}

internal fun reviewedSurfaceViolations(
    relativePath: String,
    metrics: JavaSourceShapeMetrics,
    reviewedSurface: ReviewedJavaSourceSurface,
    defaultBudget: JavaSourceShapeBudget,
    currentDate: LocalDate = LocalDate.now(),
): List<String> {
    val violations = mutableListOf<String>()
    val approval = reviewedSurface.approval
    if (currentDate.isAfter(approval.expiresOn)) {
        violations +=
            "$relativePath: reviewed surface waiver for ${reviewedSurface.owner} expired on ${approval.expiresOn}; ${reviewedSurface.splitTrigger}"
    }
    if (reviewedWaiverIsUnnecessary(relativePath, metrics, defaultBudget)) {
        violations +=
            "$relativePath: reviewed surface waiver for ${reviewedSurface.owner} is no longer needed because the file fits the ${defaultBudget.roleName} budget; remove the reviewed waiver instead of carrying a stale exception."
        return violations
    }
    violations +=
        reviewedSurfaceSnapshotDriftViolations(
            relativePath = relativePath,
            reviewedSurface = reviewedSurface,
            metrics = metrics,
            approval = approval,
        )
    return violations
}

internal fun missingReviewedSurfaceViolations(
    reviewedSurfaces: List<ReviewedJavaSourceSurface>,
    projectScope: String,
    existingRelativePaths: Set<String>,
): List<String> =
    reviewedSurfaces
        .filterNot { it.relativePath in existingRelativePaths }
        .map { reviewedSurface ->
            "${reviewedSurface.relativePath}: reviewed surface for ${reviewedSurface.owner} no longer resolves inside $projectScope; remove or rewrite the orphaned waiver instead of carrying dead metadata."
        }

private fun approvalExceedsDefaultBudget(
    approval: ReviewedJavaSourceApproval,
    defaultBudget: JavaSourceShapeBudget,
): Boolean =
    approval.approvedShape.physicalLineCount > defaultBudget.maxPhysicalLines ||
        approval.approvedShape.logicalLineCount > defaultBudget.maxLogicalLines ||
        approval.approvedShape.importCount > defaultBudget.maxImports ||
        approval.approvedShape.nestedTypeCount > defaultBudget.maxNestedTypes ||
        approval.approvedShape.maxMethodsPerTopLevelType > defaultBudget.maxMethodsPerTopLevelType ||
        approval.approvedShape.maxFieldsPerTopLevelType > defaultBudget.maxFieldsPerTopLevelType ||
        approval.approvedShape.maxSwitchArmsPerMethod > defaultBudget.maxSwitchArmsPerMethod ||
        approval.approvedShape.maxMethodLineSpan > defaultBudget.maxMethodLineSpan ||
        approval.approvedShape.maxMethodParameters > defaultBudget.maxMethodParameters ||
        approval.approvedShape.maxMethodDecisionPoints > defaultBudget.maxMethodDecisionPoints

private fun reviewedSurfaceSnapshotDriftViolations(
    relativePath: String,
    reviewedSurface: ReviewedJavaSourceSurface,
    metrics: JavaSourceShapeMetrics,
    approval: ReviewedJavaSourceApproval,
): List<String> {
    val approvedShape = approval.approvedShape
    val driftDimensions =
        listOf(
            Triple("physical lines", approvedShape.physicalLineCount, metrics.physicalLineCount),
            Triple("logical lines", approvedShape.logicalLineCount, metrics.logicalLineCount),
            Triple("imports", approvedShape.importCount, metrics.importCount),
            Triple("nested types", approvedShape.nestedTypeCount, metrics.nestedTypeCount),
            Triple(
                "methods on one top-level type",
                approvedShape.maxMethodsPerTopLevelType,
                metrics.maxMethodsPerTopLevelType,
            ),
            Triple(
                "fields on one top-level type",
                approvedShape.maxFieldsPerTopLevelType,
                metrics.maxFieldsPerTopLevelType,
            ),
            Triple(
                "switch arms",
                approvedShape.maxSwitchArmsPerMethod,
                metrics.maxSwitchArmsPerMethod,
            ),
            Triple(
                "physical lines in one method",
                approvedShape.maxMethodLineSpan,
                metrics.maxMethodLineSpan,
            ),
            Triple(
                "parameters on one method",
                approvedShape.maxMethodParameters,
                metrics.maxMethodParameters,
            ),
            Triple(
                "decision points",
                approvedShape.maxMethodDecisionPoints,
                metrics.maxMethodDecisionPoints,
            ),
        )
    return driftDimensions
        .filter { (_, approvedValue, liveValue) -> approvedValue != liveValue }
        .map { (dimensionName, approvedValue, liveValue) ->
            "$relativePath: reviewed surface approval for ${reviewedSurface.owner} no longer matches the live file on $dimensionName (approved $approvedValue, live $liveValue); refresh the waiver snapshot or finish the split instead of carrying drift."
        }
}

private fun reviewedWaiverIsUnnecessary(
    relativePath: String,
    metrics: JavaSourceShapeMetrics,
    defaultBudget: JavaSourceShapeBudget,
): Boolean = javaShapeViolations(relativePath, metrics, defaultBudget).isEmpty()
