package dev.erst.fingrind.buildlogic

import java.io.File

internal data class JavaSourceShapeBudget(
    val roleName: String,
    val maxPhysicalLines: Int,
    val maxLogicalLines: Int,
    val maxImports: Int,
    val maxNestedTypes: Int,
    val maxMethodsPerTopLevelType: Int,
    val maxFieldsPerTopLevelType: Int,
    val maxSwitchArmsPerMethod: Int,
    val maxMethodLineSpan: Int,
    val maxMethodParameters: Int,
    val maxMethodDecisionPoints: Int,
)

internal data class ReviewedJavaSourceSurface(
    val relativePath: String,
    val owner: String,
    val reason: String,
    val splitTrigger: String,
    val budget: JavaSourceShapeBudget,
)

internal data class JavaSourceStructuralContract(
    val budget: JavaSourceShapeBudget,
    val reviewedSurface: ReviewedJavaSourceSurface?,
    val reviewThreshold: Double?,
)

private val productionMainBudget =
    JavaSourceShapeBudget(
        roleName = "production-main",
        maxPhysicalLines = 500,
        maxLogicalLines = 450,
        maxImports = 45,
        maxNestedTypes = 10,
        maxMethodsPerTopLevelType = 20,
        maxFieldsPerTopLevelType = 12,
        maxSwitchArmsPerMethod = 14,
        maxMethodLineSpan = 130,
        maxMethodParameters = 10,
        maxMethodDecisionPoints = 24,
    )

private val exportedPublicSeamBudget =
    JavaSourceShapeBudget(
        roleName = "exported-public-seam",
        maxPhysicalLines = 500,
        maxLogicalLines = 450,
        maxImports = 48,
        maxNestedTypes = 20,
        maxMethodsPerTopLevelType = 22,
        maxFieldsPerTopLevelType = 14,
        maxSwitchArmsPerMethod = 16,
        maxMethodLineSpan = 140,
        maxMethodParameters = 16,
        maxMethodDecisionPoints = 24,
    )

private val testBudget =
    JavaSourceShapeBudget(
        roleName = "test-suite",
        maxPhysicalLines = 1150,
        maxLogicalLines = 1000,
        maxImports = 80,
        maxNestedTypes = 48,
        maxMethodsPerTopLevelType = 40,
        maxFieldsPerTopLevelType = 32,
        maxSwitchArmsPerMethod = 20,
        maxMethodLineSpan = 260,
        maxMethodParameters = 10,
        maxMethodDecisionPoints = 32,
    )

private val cliJsonAggregateBudget =
    JavaSourceShapeBudget(
        roleName = "cli-json-aggregate",
        maxPhysicalLines = 650,
        maxLogicalLines = 560,
        maxImports = 38,
        maxNestedTypes = 28,
        maxMethodsPerTopLevelType = 18,
        maxFieldsPerTopLevelType = 18,
        maxSwitchArmsPerMethod = 14,
        maxMethodLineSpan = 90,
        maxMethodParameters = 18,
        maxMethodDecisionPoints = 18,
    )

private val testFixturesBudget =
    JavaSourceShapeBudget(
        roleName = "test-fixtures",
        maxPhysicalLines = 1000,
        maxLogicalLines = 860,
        maxImports = 60,
        maxNestedTypes = 24,
        maxMethodsPerTopLevelType = 30,
        maxFieldsPerTopLevelType = 20,
        maxSwitchArmsPerMethod = 18,
        maxMethodLineSpan = 180,
        maxMethodParameters = 10,
        maxMethodDecisionPoints = 26,
    )

private val fuzzBudget =
    JavaSourceShapeBudget(
        roleName = "fuzz-suite",
        maxPhysicalLines = 1000,
        maxLogicalLines = 860,
        maxImports = 60,
        maxNestedTypes = 24,
        maxMethodsPerTopLevelType = 24,
        maxFieldsPerTopLevelType = 16,
        maxSwitchArmsPerMethod = 18,
        maxMethodLineSpan = 200,
        maxMethodParameters = 10,
        maxMethodDecisionPoints = 28,
    )

internal val forbiddenGenericClassNamePattern =
    Regex("""^(?:[A-Z][A-Za-z0-9]*)?(?:Manager|Helper|Util|Common|Processor)\.java$""")

private val reviewedSurfaceEntries =
    listOf(
        ReviewedJavaSourceSurface(
            relativePath = "src/main/java/dev/erst/fingrind/contract/discovery/ContractTemplates.java",
            owner = "protocol-discovery",
            reason =
                "Public discovery template namespace intentionally owns one nested contract-model family after validator logic was extracted into focused support owners.",
            splitTrigger =
                "Split into narrower top-level descriptor families before adding another template branch or another plan-step surface.",
            budget =
                JavaSourceShapeBudget(
                    roleName = "contract-template-namespace",
                    maxPhysicalLines = 500,
                    maxLogicalLines = 450,
                    maxImports = 40,
                    maxNestedTypes = 16,
                    maxMethodsPerTopLevelType = 18,
                    maxFieldsPerTopLevelType = 12,
                    maxSwitchArmsPerMethod = 10,
                    maxMethodLineSpan = 70,
                    maxMethodParameters = 8,
                    maxMethodDecisionPoints = 16,
                ),
        ),
        ReviewedJavaSourceSurface(
            relativePath = "src/main/java/dev/erst/fingrind/cli/json/CliRejectionJsonModels.java",
            owner = "cli-json-contract",
            reason =
                "The CLI rejection JSON aggregate remains one explicit machine-contract namespace, with the public rejection envelope vocabulary grouped in one place.",
            splitTrigger =
                "Split by rejection family before adding another unrelated rejection namespace or serializer concern.",
            budget =
                JavaSourceShapeBudget(
                    roleName = "cli-rejection-json-aggregate",
                    maxPhysicalLines = 460,
                    maxLogicalLines = 420,
                    maxImports = 36,
                    maxNestedTypes = 50,
                    maxMethodsPerTopLevelType = 18,
                    maxFieldsPerTopLevelType = 18,
                    maxSwitchArmsPerMethod = 10,
                    maxMethodLineSpan = 60,
                    maxMethodParameters = 6,
                    maxMethodDecisionPoints = 14,
                ),
        ),
        ReviewedJavaSourceSurface(
            relativePath = "src/main/java/dev/erst/fingrind/executor/workflow/BookWorkflowExecutionService.java",
            owner = "executor-workflow",
            reason =
                "The workflow execution service intentionally aggregates the public workflow dispatch seam, but it is reviewed as a large surface instead of being allowed to grow implicitly.",
            splitTrigger =
                "Split by workflow family before adding another command decision branch or another execution collaborator cluster.",
            budget =
                JavaSourceShapeBudget(
                    roleName = "workflow-execution-service",
                    maxPhysicalLines = 420,
                    maxLogicalLines = 380,
                    maxImports = 44,
                    maxNestedTypes = 4,
                    maxMethodsPerTopLevelType = 18,
                    maxFieldsPerTopLevelType = 10,
                    maxSwitchArmsPerMethod = 10,
                    maxMethodLineSpan = 85,
                    maxMethodParameters = 10,
                    maxMethodDecisionPoints = 18,
                ),
        ),
        ReviewedJavaSourceSurface(
            relativePath = "src/main/java/dev/erst/fingrind/sqlite/internal/SqliteNativeCalls.java",
            owner = "sqlite-native-bridge",
            reason =
                "The native symbol table remains one explicitly reviewed internal bridge surface while the SQLite FFM seam stays narrowed elsewhere.",
            splitTrigger =
                "Split the native bridge by lifecycle, statement, and metadata call families before adding another native operation cluster.",
            budget =
                JavaSourceShapeBudget(
                    roleName = "sqlite-native-call-table",
                    maxPhysicalLines = 520,
                    maxLogicalLines = 460,
                    maxImports = 28,
                    maxNestedTypes = 24,
                    maxMethodsPerTopLevelType = 18,
                    maxFieldsPerTopLevelType = 12,
                    maxSwitchArmsPerMethod = 8,
                    maxMethodLineSpan = 70,
                    maxMethodParameters = 12,
                    maxMethodDecisionPoints = 12,
                ),
        ),
        ReviewedJavaSourceSurface(
            relativePath = "src/main/java/dev/erst/fingrind/sqlite/SqlitePostingSqlLiterals.java",
            owner = "sqlite-posting-read-write",
            reason =
                "The posting SQL literal catalog is a pure reviewed data surface whose behavioral query assembly now lives in a separate owner.",
            splitTrigger =
                "Split by query family or generate the catalog before adding another unrelated statement cluster or another behavioral helper.",
            budget =
                JavaSourceShapeBudget(
                    roleName = "sqlite-posting-sql-catalog",
                    maxPhysicalLines = 900,
                    maxLogicalLines = 820,
                    maxImports = 20,
                    maxNestedTypes = 2,
                    maxMethodsPerTopLevelType = 4,
                    maxFieldsPerTopLevelType = 0,
                    maxSwitchArmsPerMethod = 2,
                    maxMethodLineSpan = 20,
                    maxMethodParameters = 0,
                    maxMethodDecisionPoints = 2,
                ),
        ),
        ReviewedJavaSourceSurface(
            relativePath = "src/main/java/dev/erst/fingrind/cli/CliIncomeStatementReportRenderer.java",
            owner = "cli-reporting",
            reason =
                "The income-statement text and CSV renderer intentionally keeps one report-family rendering narrative together.",
            splitTrigger =
                "Split by rendering mode or section owner before adding another statement family or another output mode.",
            budget =
                JavaSourceShapeBudget(
                    roleName = "cli-income-statement-renderer",
                    maxPhysicalLines = 480,
                    maxLogicalLines = 420,
                    maxImports = 40,
                    maxNestedTypes = 6,
                    maxMethodsPerTopLevelType = 20,
                    maxFieldsPerTopLevelType = 10,
                    maxSwitchArmsPerMethod = 10,
                    maxMethodLineSpan = 180,
                    maxMethodParameters = 10,
                    maxMethodDecisionPoints = 20,
                ),
        ),
        ReviewedJavaSourceSurface(
            relativePath = "src/main/java/dev/erst/fingrind/cli/CliPeriodSummaryReportRenderer.java",
            owner = "cli-reporting",
            reason =
                "The period-summary renderer remains one reviewed report-family surface while FinGrind keeps the report package split by statement family.",
            splitTrigger =
                "Split by header, totals, and row rendering owners before adding another period-summary output concern.",
            budget =
                JavaSourceShapeBudget(
                    roleName = "cli-period-summary-renderer",
                    maxPhysicalLines = 520,
                    maxLogicalLines = 450,
                    maxImports = 40,
                    maxNestedTypes = 6,
                    maxMethodsPerTopLevelType = 20,
                    maxFieldsPerTopLevelType = 10,
                    maxSwitchArmsPerMethod = 10,
                    maxMethodLineSpan = 240,
                    maxMethodParameters = 10,
                    maxMethodDecisionPoints = 20,
                ),
        ),
        ReviewedJavaSourceSurface(
            relativePath = "src/main/java/dev/erst/fingrind/contract/bookkeeping/RejectionNarrative.java",
            owner = "contract-bookkeeping",
            reason =
                "The published bookkeeping rejection narrative remains one reviewed language surface that maps closed rejection families into public text.",
            splitTrigger =
                "Split by rejection family before adding another unrelated narrative vocabulary branch.",
            budget =
                JavaSourceShapeBudget(
                    roleName = "bookkeeping-rejection-narrative",
                    maxPhysicalLines = 500,
                    maxLogicalLines = 450,
                    maxImports = 42,
                    maxNestedTypes = 8,
                    maxMethodsPerTopLevelType = 20,
                    maxFieldsPerTopLevelType = 10,
                    maxSwitchArmsPerMethod = 20,
                    maxMethodLineSpan = 140,
                    maxMethodParameters = 12,
                    maxMethodDecisionPoints = 24,
                ),
        ),
        ReviewedJavaSourceSurface(
            relativePath = "src/main/java/dev/erst/fingrind/contract/protocol/ProtocolAdministrationOperations.java",
            owner = "protocol-catalog",
            reason =
                "The administration operation inventory is one reviewed protocol catalog surface rather than implicit spread across multiple owners.",
            splitTrigger =
                "Split by operation family before adding another independently published administration contract cluster.",
            budget =
                JavaSourceShapeBudget(
                    roleName = "protocol-administration-catalog",
                    maxPhysicalLines = 520,
                    maxLogicalLines = 470,
                    maxImports = 44,
                    maxNestedTypes = 10,
                    maxMethodsPerTopLevelType = 22,
                    maxFieldsPerTopLevelType = 12,
                    maxSwitchArmsPerMethod = 20,
                    maxMethodLineSpan = 320,
                    maxMethodParameters = 16,
                    maxMethodDecisionPoints = 24,
                ),
        ),
        ReviewedJavaSourceSurface(
            relativePath = "src/main/java/dev/erst/fingrind/contract/protocol/ProtocolQueryOperations.java",
            owner = "protocol-catalog",
            reason =
                "The query operation inventory is one reviewed protocol catalog surface rather than hidden accumulation across adjacent files.",
            splitTrigger =
                "Split by query family before adding another independently published query contract cluster.",
            budget =
                JavaSourceShapeBudget(
                    roleName = "protocol-query-catalog",
                    maxPhysicalLines = 560,
                    maxLogicalLines = 500,
                    maxImports = 44,
                    maxNestedTypes = 10,
                    maxMethodsPerTopLevelType = 22,
                    maxFieldsPerTopLevelType = 12,
                    maxSwitchArmsPerMethod = 20,
                    maxMethodLineSpan = 360,
                    maxMethodParameters = 16,
                    maxMethodDecisionPoints = 24,
                ),
        ),
        ReviewedJavaSourceSurface(
            relativePath = "src/test/java/dev/erst/fingrind/executor/ProtectedBookMaintenanceServiceTest.java",
            owner = "executor-maintenance-tests",
            reason =
                "The maintenance workflow regression suite intentionally spans the full maintenance lifecycle matrix in one reviewed executable specification surface.",
            splitTrigger =
                "Split by backup, restore, and rollback scenario families before adding another major maintenance capability branch.",
            budget =
                JavaSourceShapeBudget(
                    roleName = "maintenance-lifecycle-test",
                    maxPhysicalLines = 1200,
                    maxLogicalLines = 1050,
                    maxImports = 80,
                    maxNestedTypes = 48,
                    maxMethodsPerTopLevelType = 44,
                    maxFieldsPerTopLevelType = 32,
                    maxSwitchArmsPerMethod = 20,
                    maxMethodLineSpan = 260,
                    maxMethodParameters = 10,
                    maxMethodDecisionPoints = 32,
                ),
        ),
        ReviewedJavaSourceSurface(
            relativePath = "src/test/java/dev/erst/fingrind/sqlite/SqliteBookSchemaContractTest.java",
            owner = "sqlite-schema-tests",
            reason =
                "The canonical SQLite schema contract suite intentionally proves one large schema surface against one executable fixture matrix.",
            splitTrigger =
                "Split by schema concern before adding another independent schema contract family.",
            budget =
                JavaSourceShapeBudget(
                    roleName = "sqlite-schema-contract-test",
                    maxPhysicalLines = 1400,
                    maxLogicalLines = 1150,
                    maxImports = 84,
                    maxNestedTypes = 48,
                    maxMethodsPerTopLevelType = 44,
                    maxFieldsPerTopLevelType = 32,
                    maxSwitchArmsPerMethod = 20,
                    maxMethodLineSpan = 260,
                    maxMethodParameters = 10,
                    maxMethodDecisionPoints = 32,
                ),
        ),
        ReviewedJavaSourceSurface(
            relativePath = "src/test/java/dev/erst/fingrind/sqlite/SqliteRuntimeProbeStatusTest.java",
            owner = "sqlite-runtime-tests",
            reason =
                "The runtime probe status suite intentionally covers the public runtime status lattice in one reviewed matrix.",
            splitTrigger =
                "Split by runtime state family before adding another probe-status branch.",
            budget =
                JavaSourceShapeBudget(
                    roleName = "sqlite-runtime-probe-test",
                    maxPhysicalLines = 1200,
                    maxLogicalLines = 1050,
                    maxImports = 80,
                    maxNestedTypes = 48,
                    maxMethodsPerTopLevelType = 40,
                    maxFieldsPerTopLevelType = 32,
                    maxSwitchArmsPerMethod = 20,
                    maxMethodLineSpan = 380,
                    maxMethodParameters = 10,
                    maxMethodDecisionPoints = 32,
                ),
        ),
        ReviewedJavaSourceSurface(
            relativePath = "src/test/java/dev/erst/fingrind/cli/CliMaintenanceCoverageTest.java",
            owner = "cli-maintenance-tests",
            reason =
                "The CLI maintenance coverage suite intentionally spans the maintenance command matrix in one reviewed executable contract.",
            splitTrigger =
                "Split by maintenance command family before adding another broad matrix branch.",
            budget =
                JavaSourceShapeBudget(
                    roleName = "cli-maintenance-coverage-test",
                    maxPhysicalLines = 1200,
                    maxLogicalLines = 1050,
                    maxImports = 80,
                    maxNestedTypes = 48,
                    maxMethodsPerTopLevelType = 40,
                    maxFieldsPerTopLevelType = 32,
                    maxSwitchArmsPerMethod = 20,
                    maxMethodLineSpan = 360,
                    maxMethodParameters = 10,
                    maxMethodDecisionPoints = 32,
                ),
        ),
        ReviewedJavaSourceSurface(
            relativePath = "src/test/java/dev/erst/fingrind/cli/CliPublishedExampleFixtureContractTest.java",
            owner = "cli-example-tests",
            reason =
                "The published example fixture contract intentionally verifies the canonical example set in one reviewed suite.",
            splitTrigger =
                "Split by example family before adding another broad published-example matrix branch.",
            budget =
                JavaSourceShapeBudget(
                    roleName = "cli-example-fixture-contract-test",
                    maxPhysicalLines = 1200,
                    maxLogicalLines = 1050,
                    maxImports = 80,
                    maxNestedTypes = 48,
                    maxMethodsPerTopLevelType = 40,
                    maxFieldsPerTopLevelType = 32,
                    maxSwitchArmsPerMethod = 20,
                    maxMethodLineSpan = 340,
                    maxMethodParameters = 10,
                    maxMethodDecisionPoints = 32,
                ),
        ),
        ReviewedJavaSourceSurface(
            relativePath = "src/test/java/dev/erst/fingrind/cli/CliReportArgumentParsingTest.java",
            owner = "cli-report-tests",
            reason =
                "The report argument parsing matrix intentionally covers one wide public CLI surface in one reviewed test suite.",
            splitTrigger =
                "Split by report family before adding another major argument matrix branch.",
            budget =
                JavaSourceShapeBudget(
                    roleName = "cli-report-argument-test",
                    maxPhysicalLines = 1200,
                    maxLogicalLines = 1050,
                    maxImports = 80,
                    maxNestedTypes = 48,
                    maxMethodsPerTopLevelType = 40,
                    maxFieldsPerTopLevelType = 32,
                    maxSwitchArmsPerMethod = 20,
                    maxMethodLineSpan = 380,
                    maxMethodParameters = 10,
                    maxMethodDecisionPoints = 32,
                ),
        ),
    )

internal object JavaSourceStructuralContracts {
    private val reviewedSurfaceByPath =
        reviewedSurfaceEntries.associateBy(ReviewedJavaSourceSurface::relativePath)

    fun contractFor(
        relativePath: String,
        packageName: String?,
        exportedPackages: Set<String>,
    ): JavaSourceStructuralContract {
        reviewedSurfaceByPath[relativePath]?.let { reviewedSurface ->
            return JavaSourceStructuralContract(
                budget = reviewedSurface.budget,
                reviewedSurface = reviewedSurface,
                reviewThreshold = null,
            )
        }
        return when {
            "src/testFixtures/java/" in relativePath ->
                JavaSourceStructuralContract(
                    budget = testFixturesBudget,
                    reviewedSurface = null,
                    reviewThreshold = null,
                )
            "src/test/java/" in relativePath ->
                JavaSourceStructuralContract(
                    budget = testBudget,
                    reviewedSurface = null,
                    reviewThreshold = null,
                )
            "src/fuzz/java/" in relativePath ->
                JavaSourceStructuralContract(
                    budget = fuzzBudget,
                    reviewedSurface = null,
                    reviewThreshold = null,
                )
            "src/main/java/dev/erst/fingrind/cli/json/" in relativePath ->
                JavaSourceStructuralContract(
                    budget = cliJsonAggregateBudget,
                    reviewedSurface = null,
                    reviewThreshold = null,
                )
            packageName != null && packageName in exportedPackages ->
                JavaSourceStructuralContract(
                    budget = exportedPublicSeamBudget,
                    reviewedSurface = null,
                    reviewThreshold = null,
                )
            else ->
                JavaSourceStructuralContract(
                    budget = productionMainBudget,
                    reviewedSurface = null,
                    reviewThreshold = null,
                )
        }
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
    ): Boolean {
        val roleName = contractFor(relativePath, packageName, exportedPackages).budget.roleName
        return roleName !in
            setOf(
                "contract-template-namespace",
                "cli-rejection-json-aggregate",
            )
    }
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
