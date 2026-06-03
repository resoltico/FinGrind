package dev.erst.fingrind.buildlogic

internal val reviewedJavaCliTestSourceSurfaces =
    listOf(
        reviewedJavaSourceSurface(
            relativePath = "src/test/java/dev/erst/fingrind/cli/CliAdministrativeArgumentParsingTest.java",
            owner = "cli-argument-tests",
            reason =
                "The administrative argument parsing suite remains one reviewed executable surface for a broad public CLI grammar family.",
            splitTrigger =
                "Split by administrative command family before adding another major argument matrix branch.",
            roleName = "cli-administrative-argument-test",
            physicalLines = 975,
            logicalLines = 917,
            imports = 10,
            nestedTypes = 48,
            methodsPerTopLevelType = 40,
            fieldsPerTopLevelType = 32,
            switchArmsPerMethod = 20,
            methodLineSpan = 360,
            methodParameters = 10,
            methodDecisionPoints = 32,
        ),
        reviewedJavaSourceSurface(
            relativePath = "src/test/java/dev/erst/fingrind/cli/CliMaintenanceCoverageTest.java",
            owner = "cli-maintenance-tests",
            reason =
                "The CLI maintenance coverage suite intentionally spans the maintenance command matrix in one reviewed executable contract.",
            splitTrigger =
                "Split by maintenance command family before adding another broad matrix branch.",
            roleName = "cli-maintenance-coverage-test",
            physicalLines = 1015,
            logicalLines = 955,
            imports = 23,
            nestedTypes = 48,
            methodsPerTopLevelType = 40,
            fieldsPerTopLevelType = 32,
            switchArmsPerMethod = 20,
            methodLineSpan = 360,
            methodParameters = 10,
            methodDecisionPoints = 32,
        ),
        reviewedJavaSourceSurface(
            relativePath = "src/test/java/dev/erst/fingrind/cli/CliPublishedExampleFixtureContractTest.java",
            owner = "cli-example-tests",
            reason =
                "The published example fixture contract intentionally verifies the canonical example set in one reviewed suite.",
            splitTrigger =
                "Split by example family before adding another broad published-example matrix branch.",
            roleName = "cli-example-fixture-contract-test",
            physicalLines = 332,
            logicalLines = 318,
            imports = 11,
            nestedTypes = 20,
            methodsPerTopLevelType = 24,
            fieldsPerTopLevelType = 12,
            switchArmsPerMethod = 12,
            methodLineSpan = 340,
            methodParameters = 10,
            methodDecisionPoints = 20,
        ),
        reviewedJavaSourceSurface(
            relativePath = "src/test/java/dev/erst/fingrind/cli/CliQueryOutputRendererTest.java",
            owner = "cli-query-tests",
            reason =
                "The query output renderer suite remains one reviewed executable matrix over a broad text, JSON, CSV, and PDF surface.",
            splitTrigger =
                "Split by query family or output mode before adding another large renderer contract branch.",
            roleName = "cli-query-output-renderer-test",
            physicalLines = 1001,
            logicalLines = 950,
            imports = 56,
            nestedTypes = 48,
            methodsPerTopLevelType = 40,
            fieldsPerTopLevelType = 32,
            switchArmsPerMethod = 20,
            methodLineSpan = 360,
            methodParameters = 10,
            methodDecisionPoints = 32,
        ),
        reviewedJavaSourceSurface(
            relativePath = "src/test/java/dev/erst/fingrind/cli/CliRecordingWorkflow.java",
            owner = "cli-test-support",
            reason =
                "The recording workflow test double remains one reviewed executable support surface for broad CLI interaction capture.",
            splitTrigger =
                "Split by bookkeeping, maintenance, and reporting capture families before adding another support concern.",
            roleName = "cli-recording-workflow-test-support",
            physicalLines = 303,
            logicalLines = 260,
            imports = 22,
            nestedTypes = 8,
            methodsPerTopLevelType = 44,
            fieldsPerTopLevelType = 36,
            switchArmsPerMethod = 10,
            methodLineSpan = 120,
            methodParameters = 10,
            methodDecisionPoints = 18,
        ),
        reviewedJavaSourceSurface(
            relativePath = "src/test/java/dev/erst/fingrind/cli/CliReportArgumentParsingTest.java",
            owner = "cli-report-tests",
            reason =
                "The report argument parsing matrix intentionally covers one wide public CLI surface in one reviewed test suite.",
            splitTrigger =
                "Split by report family before adding another major argument matrix branch.",
            roleName = "cli-report-argument-test",
            physicalLines = 563,
            logicalLines = 558,
            imports = 8,
            nestedTypes = 24,
            methodsPerTopLevelType = 28,
            fieldsPerTopLevelType = 12,
            switchArmsPerMethod = 12,
            methodLineSpan = 380,
            methodParameters = 10,
            methodDecisionPoints = 22,
        ),
    )
