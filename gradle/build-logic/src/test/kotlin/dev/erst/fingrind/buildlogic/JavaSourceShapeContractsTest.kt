package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JavaSourceShapeContractsTest {
    private val repositoryRoot = Path.of("").toAbsolutePath().normalize().parent.parent

    @Test
    fun exportedMainFiles_useExportedBudgetWhenNoReviewedContractExists() {
        val contract =
            JavaSourceStructuralContracts.contractFor(
                projectRootDirectory = repositoryRoot,
                projectPath = FinGrindProjectPaths.CONTRACT,
                relativePath =
                    "src/main/java/dev/erst/fingrind/contract/protocol/OperationCategory.java",
                packageName = "dev.erst.fingrind.contract.protocol",
                exportedPackages = setOf("dev.erst.fingrind.contract.protocol"),
            )

        assertEquals("exported-public-seam", contract.defaultBudget.roleName)
        assertEquals("exported-public-seam", contract.activeRoleName)
        assertNull(contract.reviewedSurface)
    }

    @Test
    fun reviewedPaths_publishReviewedRolesInsteadOfSmugglingReviewedBudgets() {
        val contract =
            JavaSourceStructuralContracts.contractFor(
                projectRootDirectory = repositoryRoot,
                projectPath = FinGrindProjectPaths.CONTRACT,
                relativePath =
                    "src/main/java/dev/erst/fingrind/contract/bookkeeping/RejectionNarrative.java",
                packageName = "dev.erst.fingrind.contract.bookkeeping",
                exportedPackages = setOf("dev.erst.fingrind.contract.bookkeeping"),
            )

        val reviewedSurface = assertNotNull(contract.reviewedSurface)
        assertEquals("contract-bookkeeping", reviewedSurface.owner)
        assertEquals("exported-public-seam", contract.defaultBudget.roleName)
        assertEquals("bookkeeping-rejection-narrative", contract.activeRoleName)
        assertNotNull(reviewedSurface.budgetVarianceReason)
        assertNull(reviewedSurface.duplicationExemptionReason)
        assertTrue(reviewedSurface.approval.expiresOn.isAfter(LocalDate.now()))
    }

    @Test
    fun reviewedPaths_resolveAcrossAllOwnedProjects() {
        val executorContract =
            JavaSourceStructuralContracts.contractFor(
                projectRootDirectory = repositoryRoot,
                projectPath = FinGrindProjectPaths.EXECUTOR,
                relativePath =
                    "src/main/java/dev/erst/fingrind/executor/bookkeeping/PeriodResultTransferPlanner.java",
                packageName = "dev.erst.fingrind.executor.bookkeeping",
                exportedPackages = emptySet(),
            )
        val sqliteContract =
            JavaSourceStructuralContracts.contractFor(
                projectRootDirectory = repositoryRoot,
                projectPath = FinGrindProjectPaths.SQLITE,
                relativePath = "src/main/java/dev/erst/fingrind/sqlite/internal/SqliteNativeCalls.java",
                packageName = "dev.erst.fingrind.sqlite.internal",
                exportedPackages = emptySet(),
            )
        val cliContract =
            JavaSourceStructuralContracts.contractFor(
                projectRootDirectory = repositoryRoot,
                projectPath = FinGrindProjectPaths.CLI,
                relativePath = "src/main/java/dev/erst/fingrind/cli/json/CliPlanJsonModels.java",
                packageName = "dev.erst.fingrind.cli.json",
                exportedPackages = emptySet(),
            )

        assertNull(executorContract.reviewedSurface)
        assertEquals("production-main", executorContract.activeRoleName)
        assertNotNull(sqliteContract.reviewedSurface)
        assertEquals("sqlite-native-call-table", sqliteContract.activeRoleName)
        assertNotNull(cliContract.reviewedSurface)
        assertEquals("cli-plan-json-aggregate", cliContract.activeRoleName)
    }

    @Test
    fun mainFilesUseTheProductionBudgetWithoutNameBasedPrivilege() {
        val contract =
            JavaSourceStructuralContracts.contractFor(
                projectRootDirectory = repositoryRoot,
                projectPath = FinGrindProjectPaths.EXECUTOR,
                relativePath =
                    "src/main/java/dev/erst/fingrind/executor/maintenance/SomeWorkflow.java",
                packageName = "dev.erst.fingrind.executor.maintenance",
                exportedPackages = emptySet(),
            )

        assertEquals("production-main", contract.defaultBudget.roleName)
        assertEquals("production-main", contract.activeRoleName)
        assertNull(contract.reviewedSurface)
    }

    @Test
    fun reviewedSurfaces_expireSoonAndFreezeApprovedShape() {
        JavaSourceStructuralContracts.reviewedSurfaces(repositoryRoot).forEach { reviewedSurface ->
            assertTrue(
                reviewedSurface.approval.expiresOn.isAfter(LocalDate.now()),
                "reviewed surface waivers must expire in the future: ${reviewedSurface.relativePath}",
            )
            assertTrue(
                reviewedSurface.approval.expiresOn <= LocalDate.now().plusDays(120),
                "reviewed surface waivers must stay short-lived: ${reviewedSurface.relativePath}",
            )
            val sourceFile =
                repositoryRoot.resolve(reviewedSurface.projectPath).resolve(reviewedSurface.relativePath)
            assertTrue(
                sourceFile.toFile().isFile,
                "reviewed surface source file must resolve inside the repository: ${reviewedSurface.relativePath}",
            )
            assertEquals(
                reviewedSurface.approval.approvedShape,
                JavaSourceShapeMetrics.measure(sourceFile.toFile()),
                "reviewed surface approvals must match the exact live snapshot: ${reviewedSurface.relativePath}",
            )
        }
    }

    @Test
    fun duplicationChecks_followReviewedSurfaceMetadataInsteadOfRoleNameLiterals() {
        assertTrue(
            !JavaSourceStructuralContracts.includeInDuplicationCheck(
                projectRootDirectory = repositoryRoot,
                projectPath = FinGrindProjectPaths.CLI,
                relativePath =
                    "src/main/java/dev/erst/fingrind/cli/json/CliPlanJsonModels.java",
                packageName = "dev.erst.fingrind.cli.json",
                exportedPackages = emptySet(),
            ),
        )
        assertTrue(
            JavaSourceStructuralContracts.includeInDuplicationCheck(
                projectRootDirectory = repositoryRoot,
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
                projectRootDirectory = repositoryRoot,
                projectPath = FinGrindProjectPaths.CLI,
                existingRelativePaths = emptySet(),
            )

        assertTrue(violations.any { "CliPlanJsonModels.java" in it })
        assertTrue(violations.none { "SqliteNativeCalls.java" in it })
    }
}
