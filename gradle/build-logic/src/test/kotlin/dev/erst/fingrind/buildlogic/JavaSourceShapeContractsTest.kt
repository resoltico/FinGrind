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
    fun splitNativeCallCatalogs_useProductionBudgetsWithoutReviewedContracts() {
        val relativePaths =
            listOf(
                "src/main/java/dev/erst/fingrind/sqlite/internal/SqliteNativeAddressCalls.java",
                "src/main/java/dev/erst/fingrind/sqlite/internal/SqliteNativeIntCalls.java",
                "src/main/java/dev/erst/fingrind/sqlite/internal/SqliteNativeStatementCalls.java",
            )

        relativePaths.forEach { relativePath ->
            val contract =
                JavaSourceStructuralContracts.contractFor(
                    projectRootDirectory = repositoryRoot,
                    projectPath = FinGrindProjectPaths.SQLITE,
                    relativePath = relativePath,
                    packageName = "dev.erst.fingrind.sqlite.internal",
                    exportedPackages = emptySet(),
                )

            assertEquals("production-main", contract.defaultBudget.roleName)
            assertEquals("production-main", contract.activeRoleName)
            assertNull(contract.reviewedSurface)
        }
    }

    @Test
    fun reviewedPaths_resolveAcrossAllOwnedProjects() {
        val executorContract =
            JavaSourceStructuralContracts.contractFor(
                projectRootDirectory = repositoryRoot,
                projectPath = FinGrindProjectPaths.EXECUTOR,
                relativePath =
                    "src/main/java/dev/erst/fingrind/executor/bookkeeping/InterimResultSweepPlanner.java",
                packageName = "dev.erst.fingrind.executor.bookkeeping",
                exportedPackages = emptySet(),
            )
        val sqliteContract =
            JavaSourceStructuralContracts.contractFor(
                projectRootDirectory = repositoryRoot,
                projectPath = FinGrindProjectPaths.SQLITE,
                relativePath =
                    "src/main/java/dev/erst/fingrind/sqlite/internal/SqliteNativeIntCalls.java",
                packageName = "dev.erst.fingrind.sqlite.internal",
                exportedPackages = emptySet(),
            )
        val cliContract =
            JavaSourceStructuralContracts.contractFor(
                projectRootDirectory = repositoryRoot,
                projectPath = FinGrindProjectPaths.CLI,
                relativePath =
                    "src/test/java/dev/erst/fingrind/cli/CliReportArgumentParsingTest.java",
                packageName = "dev.erst.fingrind.cli",
                exportedPackages = emptySet(),
            )

        assertNull(executorContract.reviewedSurface)
        assertEquals("production-main", executorContract.activeRoleName)
        assertNull(sqliteContract.reviewedSurface)
        assertEquals("production-main", sqliteContract.activeRoleName)
        assertNotNull(cliContract.reviewedSurface)
        assertEquals("cli-report-argument-test", cliContract.activeRoleName)
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
    fun duplicationChecks_includeReviewedSurfacesWithoutAnExplicitExemption() {
        assertTrue(
            JavaSourceStructuralContracts.includeInDuplicationCheck(
                projectRootDirectory = repositoryRoot,
                projectPath = FinGrindProjectPaths.CLI,
                relativePath =
                    "src/test/java/dev/erst/fingrind/cli/CliReportArgumentParsingTest.java",
                packageName = "dev.erst.fingrind.cli",
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

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("CliReportArgumentParsingTest.java"))
    }
}
