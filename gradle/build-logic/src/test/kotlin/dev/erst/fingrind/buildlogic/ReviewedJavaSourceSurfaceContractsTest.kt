package dev.erst.fingrind.buildlogic

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewedJavaSourceSurfaceContractsTest {
    @Test
    fun reviewedSurfaceDefinitionViolations_requireExplicitVarianceReasonWhenBudgetWidens() {
        val reviewedSurface =
            ReviewedJavaSourceSurface(
                projectPath = FinGrindProjectPaths.CONTRACT,
                relativePath = "src/main/java/dev/erst/fingrind/example/Oversized.java",
                owner = "example",
                reason = "Example",
                splitTrigger = "Split the example owner.",
                budget =
                    JavaSourceShapeBudget(
                        roleName = "example",
                        maxPhysicalLines = 11,
                        maxLogicalLines = 11,
                        maxImports = 2,
                        maxNestedTypes = 1,
                        maxMethodsPerTopLevelType = 2,
                        maxFieldsPerTopLevelType = 1,
                        maxSwitchArmsPerMethod = 1,
                        maxMethodLineSpan = 10,
                        maxMethodParameters = 2,
                        maxMethodDecisionPoints = 2,
                    ),
                budgetVarianceReason = null,
                duplicationExemptionReason = null,
                approval =
                    reviewedApproval(
                        physicalLines = 10,
                        logicalLines = 10,
                        imports = 1,
                        nestedTypes = 1,
                        methodsPerTopLevelType = 2,
                        fieldsPerTopLevelType = 1,
                        switchArmsPerMethod = 1,
                        methodLineSpan = 10,
                        methodParameters = 2,
                        methodDecisionPoints = 2,
                        expiresOn = LocalDate.now().plusDays(14),
                    ),
            )

        val violations =
            reviewedSurfaceDefinitionViolations(
                reviewedSurface = reviewedSurface,
                defaultBudget =
                    JavaSourceShapeBudget(
                        roleName = "production-main",
                        maxPhysicalLines = 10,
                        maxLogicalLines = 10,
                        maxImports = 1,
                        maxNestedTypes = 1,
                        maxMethodsPerTopLevelType = 2,
                        maxFieldsPerTopLevelType = 1,
                        maxSwitchArmsPerMethod = 1,
                        maxMethodLineSpan = 10,
                        maxMethodParameters = 2,
                        maxMethodDecisionPoints = 2,
                    ),
            )

        assertEquals(1, violations.size)
        assertTrue("without an explicit variance reason" in violations.single())
    }

    @Test
    fun reviewedSurfaceDefinitionViolations_requireApprovalSnapshotToFitReviewedBudget() {
        val reviewedSurface =
            ReviewedJavaSourceSurface(
                projectPath = FinGrindProjectPaths.CONTRACT,
                relativePath = "src/main/java/dev/erst/fingrind/example/OversizedApproval.java",
                owner = "example",
                reason = "Example",
                splitTrigger = "Split the example owner.",
                budget =
                    JavaSourceShapeBudget(
                        roleName = "example",
                        maxPhysicalLines = 10,
                        maxLogicalLines = 10,
                        maxImports = 1,
                        maxNestedTypes = 1,
                        maxMethodsPerTopLevelType = 2,
                        maxFieldsPerTopLevelType = 1,
                        maxSwitchArmsPerMethod = 1,
                        maxMethodLineSpan = 10,
                        maxMethodParameters = 2,
                        maxMethodDecisionPoints = 2,
                    ),
                budgetVarianceReason = "Example variance.",
                duplicationExemptionReason = null,
                approval =
                    reviewedApproval(
                        physicalLines = 11,
                        logicalLines = 10,
                        imports = 1,
                        nestedTypes = 1,
                        methodsPerTopLevelType = 2,
                        fieldsPerTopLevelType = 1,
                        switchArmsPerMethod = 1,
                        methodLineSpan = 10,
                        methodParameters = 2,
                        methodDecisionPoints = 2,
                        expiresOn = LocalDate.now().plusDays(14),
                    ),
            )

        val violations =
            reviewedSurfaceDefinitionViolations(
                reviewedSurface = reviewedSurface,
                defaultBudget = productionMainBudget,
            )

        assertEquals(1, violations.size)
        assertTrue("approval snapshot" in violations.single())
    }

    @Test
    fun reviewedSurfaceViolations_failWhenFrozenShapeGrows() {
        val reviewedSurface =
            JavaSourceStructuralContracts.reviewedSurfaces()
                .first { it.relativePath.endsWith("CliRejectionJsonModels.java") }
        val approvedShape = reviewedSurface.approval.approvedShape

        val violations =
            reviewedSurfaceViolations(
                relativePath = reviewedSurface.relativePath,
                metrics =
                    JavaSourceShapeMetrics(
                        physicalLineCount = approvedShape.physicalLineCount + 1,
                        logicalLineCount = approvedShape.logicalLineCount + 1,
                        importCount = approvedShape.importCount + 1,
                        nestedTypeCount = approvedShape.nestedTypeCount,
                        maxMethodsPerTopLevelType = approvedShape.maxMethodsPerTopLevelType,
                        maxFieldsPerTopLevelType = approvedShape.maxFieldsPerTopLevelType,
                        maxSwitchArmsPerMethod = approvedShape.maxSwitchArmsPerMethod,
                        maxMethodLineSpan = approvedShape.maxMethodLineSpan,
                        maxMethodParameters = approvedShape.maxMethodParameters,
                        maxMethodDecisionPoints = approvedShape.maxMethodDecisionPoints,
                    ),
                reviewedSurface = reviewedSurface,
                defaultBudget = cliJsonFamilyBudget,
            )

        assertEquals(3, violations.size)
        assertTrue(violations.all { "CliRejectionJsonModels.java" in it })
    }

    @Test
    fun reviewedSurfaceViolations_failWhenReviewedWaiverBecomesUnnecessary() {
        val reviewedSurface =
            ReviewedJavaSourceSurface(
                projectPath = FinGrindProjectPaths.CONTRACT,
                relativePath = "src/main/java/dev/erst/fingrind/example/ExampleOwner.java",
                owner = "example",
                reason = "Example",
                splitTrigger = "Split the example owner.",
                budget =
                    JavaSourceShapeBudget(
                        roleName = "reviewed-production-main",
                        maxPhysicalLines = 20,
                        maxLogicalLines = 20,
                        maxImports = 4,
                        maxNestedTypes = 2,
                        maxMethodsPerTopLevelType = 4,
                        maxFieldsPerTopLevelType = 2,
                        maxSwitchArmsPerMethod = 2,
                        maxMethodLineSpan = 20,
                        maxMethodParameters = 4,
                        maxMethodDecisionPoints = 4,
                    ),
                budgetVarianceReason = "Example variance.",
                duplicationExemptionReason = null,
                approval =
                    reviewedApproval(
                        physicalLines = 20,
                        logicalLines = 20,
                        imports = 4,
                        nestedTypes = 2,
                        methodsPerTopLevelType = 4,
                        fieldsPerTopLevelType = 2,
                        switchArmsPerMethod = 2,
                        methodLineSpan = 20,
                        methodParameters = 4,
                        methodDecisionPoints = 4,
                        expiresOn = LocalDate.now().plusDays(30),
                    ),
            )
        val defaultBudget =
            JavaSourceShapeBudget(
                roleName = "production-main",
                maxPhysicalLines = 30,
                maxLogicalLines = 30,
                maxImports = 5,
                maxNestedTypes = 2,
                maxMethodsPerTopLevelType = 4,
                maxFieldsPerTopLevelType = 2,
                maxSwitchArmsPerMethod = 2,
                maxMethodLineSpan = 20,
                maxMethodParameters = 4,
                maxMethodDecisionPoints = 4,
            )

        val violations =
            reviewedSurfaceViolations(
                relativePath = reviewedSurface.relativePath,
                metrics =
                    JavaSourceShapeMetrics(
                        physicalLineCount = 18,
                        logicalLineCount = 18,
                        importCount = 3,
                        nestedTypeCount = 1,
                        maxMethodsPerTopLevelType = 2,
                        maxFieldsPerTopLevelType = 1,
                        maxSwitchArmsPerMethod = 1,
                        maxMethodLineSpan = 10,
                        maxMethodParameters = 2,
                        maxMethodDecisionPoints = 2,
                    ),
                reviewedSurface = reviewedSurface,
                defaultBudget = defaultBudget,
            )

        assertEquals(1, violations.size)
        assertTrue("is no longer needed" in violations.single())
    }
}
