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
                projectPath = FinGrindProjectPaths.CONTRACT,
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
                projectPath = FinGrindProjectPaths.CONTRACT,
                relativePath =
                    "src/main/java/dev/erst/fingrind/contract/bookkeeping/RejectionNarrative.java",
                packageName = "dev.erst.fingrind.contract.bookkeeping",
                exportedPackages = setOf("dev.erst.fingrind.contract.bookkeeping"),
            )

        val reviewedSurface = assertNotNull(contract.reviewedSurface)
        assertEquals("contract-bookkeeping", reviewedSurface.owner)
        assertEquals("bookkeeping-rejection-narrative", contract.budget.roleName)
        assertNotNull(reviewedSurface.budgetVarianceReason)
        assertNull(reviewedSurface.duplicationExemptionReason)
        assertTrue(reviewedSurface.approval.expiresOn.isAfter(LocalDate.now()))
    }

    @Test
    fun reviewedPaths_resolveAcrossAllOwnedProjects() {
        val executorContract =
            JavaSourceStructuralContracts.contractFor(
                projectPath = FinGrindProjectPaths.EXECUTOR,
                relativePath =
                    "src/main/java/dev/erst/fingrind/executor/bookkeeping/PeriodResultTransferPlanner.java",
                packageName = "dev.erst.fingrind.executor.bookkeeping",
                exportedPackages = setOf("dev.erst.fingrind.executor.bookkeeping"),
            )
        val cliContract =
            JavaSourceStructuralContracts.contractFor(
                projectPath = FinGrindProjectPaths.CLI,
                relativePath = "src/main/java/dev/erst/fingrind/cli/json/CliPlanJsonModels.java",
                packageName = "dev.erst.fingrind.cli.json",
                exportedPackages = emptySet(),
            )

        assertNotNull(executorContract.reviewedSurface)
        assertEquals("period-result-transfer-planner", executorContract.budget.roleName)
        assertNotNull(cliContract.reviewedSurface)
        assertEquals("cli-plan-json-aggregate", cliContract.budget.roleName)
    }

    @Test
    fun mainFilesUseTheProductionBudgetWithoutNameBasedPrivilege() {
        val contract =
            JavaSourceStructuralContracts.contractFor(
                projectPath = FinGrindProjectPaths.EXECUTOR,
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
            val approvedShape = reviewedSurface.approval.approvedShape
            assertTrue(
                approvedShape.physicalLineCount <= reviewedSurface.budget.maxPhysicalLines,
            )
            assertTrue(
                approvedShape.logicalLineCount <= reviewedSurface.budget.maxLogicalLines,
            )
            assertTrue(
                approvedShape.importCount <= reviewedSurface.budget.maxImports,
            )
            assertTrue(
                approvedShape.nestedTypeCount <= reviewedSurface.budget.maxNestedTypes,
            )
            assertTrue(
                approvedShape.maxMethodsPerTopLevelType <= reviewedSurface.budget.maxMethodsPerTopLevelType,
            )
            assertTrue(
                approvedShape.maxFieldsPerTopLevelType <= reviewedSurface.budget.maxFieldsPerTopLevelType,
            )
            assertTrue(
                approvedShape.maxSwitchArmsPerMethod <= reviewedSurface.budget.maxSwitchArmsPerMethod,
            )
            assertTrue(
                approvedShape.maxMethodLineSpan <= reviewedSurface.budget.maxMethodLineSpan,
            )
            assertTrue(
                approvedShape.maxMethodParameters <= reviewedSurface.budget.maxMethodParameters,
            )
            assertTrue(
                approvedShape.maxMethodDecisionPoints <= reviewedSurface.budget.maxMethodDecisionPoints,
            )
        }
    }

    @Test
    fun duplicationChecks_followReviewedSurfaceMetadataInsteadOfRoleNameLiterals() {
        assertTrue(
            !JavaSourceStructuralContracts.includeInDuplicationCheck(
                projectPath = FinGrindProjectPaths.CLI,
                relativePath =
                    "src/main/java/dev/erst/fingrind/cli/json/CliPlanJsonModels.java",
                packageName = "dev.erst.fingrind.cli.json",
                exportedPackages = emptySet(),
            ),
        )
        assertTrue(
            JavaSourceStructuralContracts.includeInDuplicationCheck(
                projectPath = FinGrindProjectPaths.CONTRACT,
                relativePath =
                    "src/main/java/dev/erst/fingrind/contract/protocol/ProtocolAdministrationOperations.java",
                packageName = "dev.erst.fingrind.contract.protocol",
                exportedPackages = setOf("dev.erst.fingrind.contract.protocol"),
            ),
        )
    }

    @Test
    fun missingReviewedSurfaceViolations_reportOrphanedPathsPerProject() {
        val violations =
            JavaSourceStructuralContracts.missingReviewedSurfaceViolations(
                projectPath = FinGrindProjectPaths.CLI,
                existingRelativePaths = emptySet(),
            )

        assertTrue(violations.any { "CliPlanJsonModels.java" in it })
        assertTrue(violations.none { "SqliteNativeCalls.java" in it })
    }
}
