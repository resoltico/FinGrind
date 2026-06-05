package dev.erst.fingrind.buildlogic

import java.io.File
import java.time.LocalDate

internal object JavaSourceStructuralContracts {
    private val reviewedSurfaceByPath =
        reviewedSurfaceEntries.associateBy(ReviewedJavaSourceSurface::relativePath)

    fun baselineBudgetFor(
        relativePath: String,
        packageName: String?,
        exportedPackages: Set<String>,
    ): JavaSourceShapeBudget =
        when {
            "src/testFixtures/java/" in relativePath -> testFixturesBudget
            "src/test/java/" in relativePath -> testBudget
            "src/fuzz/java/" in relativePath -> fuzzBudget
            "src/main/java/dev/erst/fingrind/cli/json/" in relativePath -> cliJsonFamilyBudget
            packageName != null && packageName in exportedPackages -> exportedPublicSeamBudget
            else -> productionMainBudget
        }

    fun contractFor(
        relativePath: String,
        packageName: String?,
        exportedPackages: Set<String>,
    ): JavaSourceStructuralContract {
        reviewedSurfaceByPath[relativePath]?.let { reviewedSurface ->
            return JavaSourceStructuralContract(
                budget = reviewedSurface.budget,
                reviewedSurface = reviewedSurface,
            )
        }
        return JavaSourceStructuralContract(
            budget = baselineBudgetFor(relativePath, packageName, exportedPackages),
            reviewedSurface = null,
        )
    }

    fun exportedPackages(projectDirectory: File): Set<String> {
        val moduleInfoFile = File(projectDirectory, "src/main/java/module-info.java")
        if (!moduleInfoFile.isFile) {
            return emptySet()
        }
        return Regex("""^\s*exports\s+([A-Za-z0-9_.]+)\s*;""", RegexOption.MULTILINE)
            .findAll(moduleInfoFile.readText())
            .map { matchResult -> matchResult.groupValues[1] }
            .toSet()
    }

    fun packageNameFor(file: File): String? =
        Regex("""^\s*package\s+([A-Za-z0-9_.]+)\s*;""", RegexOption.MULTILINE)
            .find(file.readText())
            ?.groupValues
            ?.get(1)

    fun reviewedSurfaces(): List<ReviewedJavaSourceSurface> = reviewedSurfaceEntries

    fun includeInDuplicationCheck(
        relativePath: String,
        packageName: String?,
        exportedPackages: Set<String>,
    ): Boolean =
        reviewedSurfaceByPath[relativePath]?.duplicationExemptionReason == null
}

internal fun reviewedSurfaceDefinitionViolations(
    reviewedSurface: ReviewedJavaSourceSurface,
    defaultBudget: JavaSourceShapeBudget,
): List<String> {
    val budget = reviewedSurface.budget
    if (budgetExceedsDefaultBudget(budget, defaultBudget) && reviewedSurface.budgetVarianceReason == null) {
        return listOf(
            "${reviewedSurface.relativePath}: reviewed surface for ${reviewedSurface.owner} widens the ${defaultBudget.roleName} budget without an explicit variance reason.",
        )
    }
    return emptyList()
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

internal fun javaShapeViolations(
    relativePath: String,
    metrics: JavaSourceShapeMetrics,
    budget: JavaSourceShapeBudget,
): List<String> {
    val violations = mutableListOf<String>()
    if (metrics.physicalLineCount > budget.maxPhysicalLines) {
        violations +=
            "$relativePath: ${metrics.physicalLineCount} physical lines exceeds ${budget.maxPhysicalLines} for ${budget.roleName}; split the file by responsibility."
    }
    if (metrics.logicalLineCount > budget.maxLogicalLines) {
        violations +=
            "$relativePath: ${metrics.logicalLineCount} logical lines exceeds ${budget.maxLogicalLines} for ${budget.roleName}; remove responsibility accretion."
    }
    if (metrics.importCount > budget.maxImports) {
        violations +=
            "$relativePath: ${metrics.importCount} imports exceeds ${budget.maxImports} for ${budget.roleName}; reduce fan-out or split the class."
    }
    if (metrics.nestedTypeCount > budget.maxNestedTypes) {
        violations +=
            "$relativePath: ${metrics.nestedTypeCount} nested type declarations exceeds ${budget.maxNestedTypes} for ${budget.roleName}; promote focused collaborators into their own files."
    }
    if (metrics.maxMethodsPerTopLevelType > budget.maxMethodsPerTopLevelType) {
        violations +=
            "$relativePath: ${metrics.maxMethodsPerTopLevelType} methods/constructors on one top-level type exceeds ${budget.maxMethodsPerTopLevelType} for ${budget.roleName}; split the type by responsibility."
    }
    if (metrics.maxFieldsPerTopLevelType > budget.maxFieldsPerTopLevelType) {
        violations +=
            "$relativePath: ${metrics.maxFieldsPerTopLevelType} fields on one top-level type exceeds ${budget.maxFieldsPerTopLevelType} for ${budget.roleName}; reduce retained state or split collaborators."
    }
    if (metrics.maxSwitchArmsPerMethod > budget.maxSwitchArmsPerMethod) {
        violations +=
            "$relativePath: ${metrics.maxSwitchArmsPerMethod} switch arms in one method exceeds ${budget.maxSwitchArmsPerMethod} for ${budget.roleName}; break the dispatcher into narrower owners."
    }
    if (metrics.maxMethodLineSpan > budget.maxMethodLineSpan) {
        violations +=
            "$relativePath: one method spans ${metrics.maxMethodLineSpan} physical lines, exceeding ${budget.maxMethodLineSpan} for ${budget.roleName}; split the method into named policy, mapping, or orchestration collaborators."
    }
    if (metrics.maxMethodParameters > budget.maxMethodParameters) {
        violations +=
            "$relativePath: one method declares ${metrics.maxMethodParameters} parameters, exceeding ${budget.maxMethodParameters} for ${budget.roleName}; collapse the argument surface behind one focused value object or helper seam."
    }
    if (metrics.maxMethodDecisionPoints > budget.maxMethodDecisionPoints) {
        violations +=
            "$relativePath: one method owns ${metrics.maxMethodDecisionPoints} decision points, exceeding ${budget.maxMethodDecisionPoints} for ${budget.roleName}; split the control flow into narrower owners."
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
    if (metrics.physicalLineCount > approval.approvedPhysicalLines) {
        violations +=
            "$relativePath: reviewed surface for ${reviewedSurface.owner} grew from ${approval.approvedPhysicalLines} to ${metrics.physicalLineCount} physical lines before the waiver expiry ${approval.expiresOn}; ${reviewedSurface.splitTrigger}"
    }
    if (metrics.logicalLineCount > approval.approvedLogicalLines) {
        violations +=
            "$relativePath: reviewed surface for ${reviewedSurface.owner} grew from ${approval.approvedLogicalLines} to ${metrics.logicalLineCount} logical lines before the waiver expiry ${approval.expiresOn}; ${reviewedSurface.splitTrigger}"
    }
    if (metrics.importCount > approval.approvedImports) {
        violations +=
            "$relativePath: reviewed surface for ${reviewedSurface.owner} grew from ${approval.approvedImports} to ${metrics.importCount} imports before the waiver expiry ${approval.expiresOn}; ${reviewedSurface.splitTrigger}"
    }
    if (
        reviewedSurface.duplicationExemptionReason == null &&
            reviewedWaiverIsUnnecessary(relativePath, metrics, defaultBudget)
    ) {
        violations +=
            "$relativePath: reviewed surface waiver for ${reviewedSurface.owner} is no longer needed because the file fits the ${defaultBudget.roleName} budget; remove the reviewed waiver instead of carrying a stale exception."
    }
    return violations
}
