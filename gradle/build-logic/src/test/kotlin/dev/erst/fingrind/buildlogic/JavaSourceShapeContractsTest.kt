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
            )

        assertEquals(3, violations.size)
        assertTrue(violations.all { "CliRejectionJsonModels.java" in it })
    }
}
