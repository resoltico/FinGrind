package dev.erst.fingrind.buildlogic

import java.time.LocalDate

internal fun reviewedSurfaceDefinitionViolations(
    reviewedSurface: ReviewedJavaSourceSurface,
    defaultBudget: JavaSourceShapeBudget,
): List<String> {
    val violations = mutableListOf<String>()
    val budget = reviewedSurface.budget
    if (budgetExceedsDefaultBudget(budget, defaultBudget) && reviewedSurface.budgetVarianceReason == null) {
        violations +=
            "${reviewedSurface.relativePath}: reviewed surface for ${reviewedSurface.owner} widens the ${defaultBudget.roleName} budget without an explicit variance reason."
    }
    val approvedShape = reviewedSurface.approval.approvedShape
    if (approvedShape.physicalLineCount > budget.maxPhysicalLines) {
        violations +=
            "${reviewedSurface.relativePath}: reviewed approval snapshot for ${reviewedSurface.owner} records ${approvedShape.physicalLineCount} physical lines, exceeding the reviewed ${budget.roleName} budget of ${budget.maxPhysicalLines}."
    }
    if (approvedShape.logicalLineCount > budget.maxLogicalLines) {
        violations +=
            "${reviewedSurface.relativePath}: reviewed approval snapshot for ${reviewedSurface.owner} records ${approvedShape.logicalLineCount} logical lines, exceeding the reviewed ${budget.roleName} budget of ${budget.maxLogicalLines}."
    }
    if (approvedShape.importCount > budget.maxImports) {
        violations +=
            "${reviewedSurface.relativePath}: reviewed approval snapshot for ${reviewedSurface.owner} records ${approvedShape.importCount} imports, exceeding the reviewed ${budget.roleName} budget of ${budget.maxImports}."
    }
    if (approvedShape.nestedTypeCount > budget.maxNestedTypes) {
        violations +=
            "${reviewedSurface.relativePath}: reviewed approval snapshot for ${reviewedSurface.owner} records ${approvedShape.nestedTypeCount} nested types, exceeding the reviewed ${budget.roleName} budget of ${budget.maxNestedTypes}."
    }
    if (approvedShape.maxMethodsPerTopLevelType > budget.maxMethodsPerTopLevelType) {
        violations +=
            "${reviewedSurface.relativePath}: reviewed approval snapshot for ${reviewedSurface.owner} records ${approvedShape.maxMethodsPerTopLevelType} methods on one top-level type, exceeding the reviewed ${budget.roleName} budget of ${budget.maxMethodsPerTopLevelType}."
    }
    if (approvedShape.maxFieldsPerTopLevelType > budget.maxFieldsPerTopLevelType) {
        violations +=
            "${reviewedSurface.relativePath}: reviewed approval snapshot for ${reviewedSurface.owner} records ${approvedShape.maxFieldsPerTopLevelType} fields on one top-level type, exceeding the reviewed ${budget.roleName} budget of ${budget.maxFieldsPerTopLevelType}."
    }
    if (approvedShape.maxSwitchArmsPerMethod > budget.maxSwitchArmsPerMethod) {
        violations +=
            "${reviewedSurface.relativePath}: reviewed approval snapshot for ${reviewedSurface.owner} records ${approvedShape.maxSwitchArmsPerMethod} switch arms, exceeding the reviewed ${budget.roleName} budget of ${budget.maxSwitchArmsPerMethod}."
    }
    if (approvedShape.maxMethodLineSpan > budget.maxMethodLineSpan) {
        violations +=
            "${reviewedSurface.relativePath}: reviewed approval snapshot for ${reviewedSurface.owner} records ${approvedShape.maxMethodLineSpan} physical lines in one method, exceeding the reviewed ${budget.roleName} budget of ${budget.maxMethodLineSpan}."
    }
    if (approvedShape.maxMethodParameters > budget.maxMethodParameters) {
        violations +=
            "${reviewedSurface.relativePath}: reviewed approval snapshot for ${reviewedSurface.owner} records ${approvedShape.maxMethodParameters} parameters on one method, exceeding the reviewed ${budget.roleName} budget of ${budget.maxMethodParameters}."
    }
    if (approvedShape.maxMethodDecisionPoints > budget.maxMethodDecisionPoints) {
        violations +=
            "${reviewedSurface.relativePath}: reviewed approval snapshot for ${reviewedSurface.owner} records ${approvedShape.maxMethodDecisionPoints} decision points, exceeding the reviewed ${budget.roleName} budget of ${budget.maxMethodDecisionPoints}."
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
    val approvedShape = approval.approvedShape
    if (currentDate.isAfter(approval.expiresOn)) {
        violations +=
            "$relativePath: reviewed surface waiver for ${reviewedSurface.owner} expired on ${approval.expiresOn}; ${reviewedSurface.splitTrigger}"
    }
    if (metrics.physicalLineCount > approvedShape.physicalLineCount) {
        violations +=
            "$relativePath: reviewed surface for ${reviewedSurface.owner} grew from ${approvedShape.physicalLineCount} to ${metrics.physicalLineCount} physical lines before the waiver expiry ${approval.expiresOn}; ${reviewedSurface.splitTrigger}"
    }
    if (metrics.logicalLineCount > approvedShape.logicalLineCount) {
        violations +=
            "$relativePath: reviewed surface for ${reviewedSurface.owner} grew from ${approvedShape.logicalLineCount} to ${metrics.logicalLineCount} logical lines before the waiver expiry ${approval.expiresOn}; ${reviewedSurface.splitTrigger}"
    }
    if (metrics.importCount > approvedShape.importCount) {
        violations +=
            "$relativePath: reviewed surface for ${reviewedSurface.owner} grew from ${approvedShape.importCount} to ${metrics.importCount} imports before the waiver expiry ${approval.expiresOn}; ${reviewedSurface.splitTrigger}"
    }
    if (metrics.nestedTypeCount > approvedShape.nestedTypeCount) {
        violations +=
            "$relativePath: reviewed surface for ${reviewedSurface.owner} grew from ${approvedShape.nestedTypeCount} to ${metrics.nestedTypeCount} nested types before the waiver expiry ${approval.expiresOn}; ${reviewedSurface.splitTrigger}"
    }
    if (metrics.maxMethodsPerTopLevelType > approvedShape.maxMethodsPerTopLevelType) {
        violations +=
            "$relativePath: reviewed surface for ${reviewedSurface.owner} grew from ${approvedShape.maxMethodsPerTopLevelType} to ${metrics.maxMethodsPerTopLevelType} methods on one top-level type before the waiver expiry ${approval.expiresOn}; ${reviewedSurface.splitTrigger}"
    }
    if (metrics.maxFieldsPerTopLevelType > approvedShape.maxFieldsPerTopLevelType) {
        violations +=
            "$relativePath: reviewed surface for ${reviewedSurface.owner} grew from ${approvedShape.maxFieldsPerTopLevelType} to ${metrics.maxFieldsPerTopLevelType} fields on one top-level type before the waiver expiry ${approval.expiresOn}; ${reviewedSurface.splitTrigger}"
    }
    if (metrics.maxSwitchArmsPerMethod > approvedShape.maxSwitchArmsPerMethod) {
        violations +=
            "$relativePath: reviewed surface for ${reviewedSurface.owner} grew from ${approvedShape.maxSwitchArmsPerMethod} to ${metrics.maxSwitchArmsPerMethod} switch arms before the waiver expiry ${approval.expiresOn}; ${reviewedSurface.splitTrigger}"
    }
    if (metrics.maxMethodLineSpan > approvedShape.maxMethodLineSpan) {
        violations +=
            "$relativePath: reviewed surface for ${reviewedSurface.owner} grew from ${approvedShape.maxMethodLineSpan} to ${metrics.maxMethodLineSpan} physical lines in one method before the waiver expiry ${approval.expiresOn}; ${reviewedSurface.splitTrigger}"
    }
    if (metrics.maxMethodParameters > approvedShape.maxMethodParameters) {
        violations +=
            "$relativePath: reviewed surface for ${reviewedSurface.owner} grew from ${approvedShape.maxMethodParameters} to ${metrics.maxMethodParameters} parameters on one method before the waiver expiry ${approval.expiresOn}; ${reviewedSurface.splitTrigger}"
    }
    if (metrics.maxMethodDecisionPoints > approvedShape.maxMethodDecisionPoints) {
        violations +=
            "$relativePath: reviewed surface for ${reviewedSurface.owner} grew from ${approvedShape.maxMethodDecisionPoints} to ${metrics.maxMethodDecisionPoints} decision points before the waiver expiry ${approval.expiresOn}; ${reviewedSurface.splitTrigger}"
    }
    if (reviewedWaiverIsUnnecessary(relativePath, metrics, defaultBudget)) {
        violations +=
            "$relativePath: reviewed surface waiver for ${reviewedSurface.owner} is no longer needed because the file fits the ${defaultBudget.roleName} budget; remove the reviewed waiver instead of carrying a stale exception."
    }
    return violations
}

private fun budgetExceedsDefaultBudget(
    reviewedBudget: JavaSourceShapeBudget,
    defaultBudget: JavaSourceShapeBudget,
): Boolean =
    reviewedBudget.maxPhysicalLines > defaultBudget.maxPhysicalLines ||
        reviewedBudget.maxLogicalLines > defaultBudget.maxLogicalLines ||
        reviewedBudget.maxImports > defaultBudget.maxImports ||
        reviewedBudget.maxNestedTypes > defaultBudget.maxNestedTypes ||
        reviewedBudget.maxMethodsPerTopLevelType > defaultBudget.maxMethodsPerTopLevelType ||
        reviewedBudget.maxFieldsPerTopLevelType > defaultBudget.maxFieldsPerTopLevelType ||
        reviewedBudget.maxSwitchArmsPerMethod > defaultBudget.maxSwitchArmsPerMethod ||
        reviewedBudget.maxMethodLineSpan > defaultBudget.maxMethodLineSpan ||
        reviewedBudget.maxMethodParameters > defaultBudget.maxMethodParameters ||
        reviewedBudget.maxMethodDecisionPoints > defaultBudget.maxMethodDecisionPoints

private fun reviewedWaiverIsUnnecessary(
    relativePath: String,
    metrics: JavaSourceShapeMetrics,
    defaultBudget: JavaSourceShapeBudget,
): Boolean = javaShapeViolations(relativePath, metrics, defaultBudget).isEmpty()
