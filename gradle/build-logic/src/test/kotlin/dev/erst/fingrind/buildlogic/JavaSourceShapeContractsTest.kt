package dev.erst.fingrind.buildlogic

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JavaSourceShapeContractsTest {
    @Test
    fun exportedMainFiles_useExportedBudgetWhenNoReviewedContractExists() {
        val contract =
            JavaSourceStructuralContracts.contractFor(
                relativePath =
                    "src/main/java/dev/erst/fingrind/contract/protocol/OperationCategory.java",
                packageName = "dev.erst.fingrind.contract.protocol",
                exportedPackages = setOf("dev.erst.fingrind.contract.protocol"),
            )

        assertEquals("exported-public-seam", contract.budget.roleName)
        assertNull(contract.reviewedSurface)
    }

    @Test
    fun reviewedPaths_useReviewedBudgetInsteadOfFilenameHeuristics() {
        val contract =
            JavaSourceStructuralContracts.contractFor(
                relativePath =
                    "src/main/java/dev/erst/fingrind/contract/discovery/ContractTemplates.java",
                packageName = "dev.erst.fingrind.contract.discovery",
                exportedPackages = setOf("dev.erst.fingrind.contract.discovery"),
            )

        val reviewedSurface = assertNotNull(contract.reviewedSurface)
        assertEquals("protocol-discovery", reviewedSurface.owner)
        assertEquals("contract-template-namespace", contract.budget.roleName)
        assertNotNull(reviewedSurface.budgetVarianceReason)
        assertNotNull(reviewedSurface.duplicationExemptionReason)
        assertTrue(reviewedSurface.approval.expiresOn.isAfter(LocalDate.now()))
    }

    @Test
    fun mainFilesUseTheProductionBudgetWithoutNameBasedPrivilege() {
        val contract =
            JavaSourceStructuralContracts.contractFor(
                relativePath =
                    "src/main/java/dev/erst/fingrind/executor/maintenance/SomeWorkflow.java",
                packageName = "dev.erst.fingrind.executor.maintenance",
                exportedPackages = emptySet(),
            )

        assertEquals("production-main", contract.budget.roleName)
        assertNull(contract.reviewedSurface)
    }

    @Test
    fun reviewedSurfaces_expireSoonAndFreezeApprovedShape() {
        JavaSourceStructuralContracts.reviewedSurfaces().forEach { reviewedSurface ->
            assertTrue(
                reviewedSurface.approval.expiresOn.isAfter(LocalDate.now()),
                "reviewed surface waivers must expire in the future: ${reviewedSurface.relativePath}",
            )
            assertTrue(
                reviewedSurface.approval.expiresOn <= LocalDate.now().plusDays(120),
                "reviewed surface waivers must stay short-lived: ${reviewedSurface.relativePath}",
            )
            assertTrue(
                reviewedSurface.approval.approvedPhysicalLines <= reviewedSurface.budget.maxPhysicalLines,
            )
            assertTrue(
                reviewedSurface.approval.approvedLogicalLines <= reviewedSurface.budget.maxLogicalLines,
            )
            assertTrue(
                reviewedSurface.approval.approvedImports <= reviewedSurface.budget.maxImports,
            )
        }
    }

    @Test
    fun duplicationChecks_followReviewedSurfaceMetadataInsteadOfRoleNameLiterals() {
        assertTrue(
            !JavaSourceStructuralContracts.includeInDuplicationCheck(
                relativePath =
                    "src/main/java/dev/erst/fingrind/contract/discovery/ContractTemplates.java",
                packageName = "dev.erst.fingrind.contract.discovery",
                exportedPackages = setOf("dev.erst.fingrind.contract.discovery"),
            ),
        )
        assertTrue(
            JavaSourceStructuralContracts.includeInDuplicationCheck(
                relativePath =
                    "src/main/java/dev/erst/fingrind/contract/protocol/ProtocolAdministrationOperations.java",
                packageName = "dev.erst.fingrind.contract.protocol",
                exportedPackages = setOf("dev.erst.fingrind.contract.protocol"),
            ),
        )
    }

    @Test
    fun reviewedSurfaceDefinitionViolations_requireExplicitVarianceReasonWhenBudgetWidens() {
        val reviewedSurface =
            ReviewedJavaSourceSurface(
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
                approval = ReviewedJavaSourceApproval(10, 10, 1, LocalDate.now().plusDays(14)),
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
    fun reviewedSurfaceViolations_failWhenFrozenShapeGrows() {
        val reviewedSurface =
            JavaSourceStructuralContracts.reviewedSurfaces()
                .first { it.relativePath.endsWith("CliRejectionJsonModels.java") }

        val violations =
            reviewedSurfaceViolations(
                relativePath = reviewedSurface.relativePath,
                metrics =
                    JavaSourceShapeMetrics(
                        physicalLineCount = reviewedSurface.approval.approvedPhysicalLines + 1,
                        logicalLineCount = reviewedSurface.approval.approvedLogicalLines + 1,
                        importCount = reviewedSurface.approval.approvedImports + 1,
                        nestedTypeCount = 1,
                        maxMethodsPerTopLevelType = 1,
                        maxFieldsPerTopLevelType = 1,
                        maxSwitchArmsPerMethod = 1,
                        maxMethodLineSpan = 1,
                        maxMethodParameters = 1,
                        maxMethodDecisionPoints = 1,
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
                approval = ReviewedJavaSourceApproval(20, 20, 4, LocalDate.now().plusDays(30)),
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
