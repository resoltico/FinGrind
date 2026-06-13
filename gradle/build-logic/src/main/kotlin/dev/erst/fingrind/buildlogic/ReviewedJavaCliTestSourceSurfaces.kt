package dev.erst.fingrind.buildlogic

internal val reviewedJavaCliTestSourceSurfaces =
    listOf(
        reviewedJavaSourceSurface(
            projectPath = FinGrindProjectPaths.CLI,
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
            approval =
                reviewedApproval(
                    physicalLines = 1002,
                    logicalLines = 924,
                    imports = 23,
                    nestedTypes = 0,
                    methodsPerTopLevelType = 12,
                    fieldsPerTopLevelType = 0,
                    switchArmsPerMethod = 0,
                    methodLineSpan = 341,
                    methodParameters = 3,
                    methodDecisionPoints = 1,
                    expiresOn = reviewedExpiry("2026-07-21"),
                ),
            budgetVarianceReason =
                "The maintenance coverage matrix exceeds the default test-suite budget until maintenance command families are split further.",
        ),
        reviewedJavaSourceSurface(
            projectPath = FinGrindProjectPaths.CLI,
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
            approval =
                reviewedApproval(
                    physicalLines = 302,
                    logicalLines = 259,
                    imports = 22,
                    nestedTypes = 0,
                    methodsPerTopLevelType = 40,
                    fieldsPerTopLevelType = 32,
                    switchArmsPerMethod = 0,
                    methodLineSpan = 14,
                    methodParameters = 6,
                    methodDecisionPoints = 1,
                    expiresOn = reviewedExpiry("2026-08-11"),
                ),
            budgetVarianceReason =
                "The recording workflow support owner exceeds the default test-suite budget until bookkeeping, maintenance, and reporting capture families are split further.",
        ),
        reviewedJavaSourceSurface(
            projectPath = FinGrindProjectPaths.CLI,
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
            approval =
                reviewedApproval(
                    physicalLines = 562,
                    logicalLines = 526,
                    imports = 8,
                    nestedTypes = 0,
                    methodsPerTopLevelType = 2,
                    fieldsPerTopLevelType = 0,
                    switchArmsPerMethod = 0,
                    methodLineSpan = 344,
                    methodParameters = 0,
                    methodDecisionPoints = 0,
                    expiresOn = reviewedExpiry("2026-08-18"),
                ),
            budgetVarianceReason =
                "The report argument matrix exceeds the default test-suite budget until report families are split further.",
        ),
    )
