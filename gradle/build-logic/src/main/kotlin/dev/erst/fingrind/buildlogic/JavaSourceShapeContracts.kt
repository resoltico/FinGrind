package dev.erst.fingrind.buildlogic

import java.io.File
import java.nio.file.Path

internal object JavaSourceStructuralContracts {
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
        projectRootDirectory: Path,
        projectPath: String,
        relativePath: String,
        packageName: String?,
        exportedPackages: Set<String>,
        registrySnapshot: JavaReviewedSurfaceRegistrySnapshot =
            ReviewedSurfaceRegistry.javaSnapshot(projectRootDirectory),
    ): JavaSourceStructuralContract {
        val defaultBudget = baselineBudgetFor(relativePath, packageName, exportedPackages)
        registrySnapshot.surfaceMap[
            JavaReviewedSurfaceKey(projectPath, relativePath)
        ]?.let { reviewedSurface ->
            return JavaSourceStructuralContract(
                defaultBudget = defaultBudget,
                reviewedSurface = reviewedSurface,
            )
        }
        return JavaSourceStructuralContract(
            defaultBudget = defaultBudget,
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

    fun reviewedSurfaces(
        projectRootDirectory: Path,
        registrySnapshot: JavaReviewedSurfaceRegistrySnapshot =
            ReviewedSurfaceRegistry.javaSnapshot(projectRootDirectory),
    ): List<ReviewedJavaSourceSurface> = registrySnapshot.surfaces

    fun reviewedSurfaces(
        projectRootDirectory: Path,
        projectPath: String,
        registrySnapshot: JavaReviewedSurfaceRegistrySnapshot =
            ReviewedSurfaceRegistry.javaSnapshot(projectRootDirectory),
    ): List<ReviewedJavaSourceSurface> =
        reviewedSurfaces(projectRootDirectory, registrySnapshot).filter {
            it.projectPath == projectPath
        }

    fun includeInDuplicationCheck(
        projectRootDirectory: Path,
        projectPath: String,
        relativePath: String,
        packageName: String?,
        exportedPackages: Set<String>,
        registrySnapshot: JavaReviewedSurfaceRegistrySnapshot =
            ReviewedSurfaceRegistry.javaSnapshot(projectRootDirectory),
    ): Boolean =
        registrySnapshot.surfaceMap[
            JavaReviewedSurfaceKey(projectPath, relativePath)
        ]?.duplicationExemptionReason == null

    fun missingReviewedSurfaceViolations(
        projectRootDirectory: Path,
        projectPath: String,
        existingRelativePaths: Set<String>,
        registrySnapshot: JavaReviewedSurfaceRegistrySnapshot =
            ReviewedSurfaceRegistry.javaSnapshot(projectRootDirectory),
    ): List<String> =
        missingReviewedSurfaceViolations(
            reviewedSurfaces = reviewedSurfaces(projectRootDirectory, projectPath, registrySnapshot),
            projectScope = projectPath,
            existingRelativePaths = existingRelativePaths,
        )
}

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
