package dev.erst.fingrind.buildlogic

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
        assertNull(contract.reviewThreshold)
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
        assertNull(contract.reviewThreshold)
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
}
