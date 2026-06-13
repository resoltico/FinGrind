package dev.erst.fingrind.buildlogic

internal val reviewedJavaCliSourceSurfaces =
    listOf(
        reviewedJavaSourceSurface(
            projectPath = FinGrindProjectPaths.CLI,
            relativePath = "src/main/java/dev/erst/fingrind/cli/json/CliPlanJsonModels.java",
            owner = "cli-json-contract",
            reason =
                "Ledger-plan JSON remains one machine-contract family, but it is reviewed as a frozen large surface instead of inheriting a broad package waiver.",
            splitTrigger =
                "Split by execution envelope, fact, and rejection payload families before adding another plan JSON namespace.",
            roleName = "cli-plan-json-aggregate",
            physicalLines = 368,
            logicalLines = 340,
            imports = 14,
            nestedTypes = 40,
            methodsPerTopLevelType = 18,
            fieldsPerTopLevelType = 18,
            switchArmsPerMethod = 10,
            methodLineSpan = 60,
            methodParameters = 6,
            methodDecisionPoints = 14,
            approval =
                reviewedApproval(
                    physicalLines = 313,
                    logicalLines = 290,
                    imports = 13,
                    nestedTypes = 18,
                    methodsPerTopLevelType = 0,
                    fieldsPerTopLevelType = 0,
                    switchArmsPerMethod = 0,
                    methodLineSpan = 36,
                    methodParameters = 0,
                    methodDecisionPoints = 8,
                    expiresOn = reviewedExpiry("2026-08-05"),
                ),
            budgetVarianceReason =
                "The ledger-plan JSON aggregate exceeds the default CLI JSON budget until envelope, fact, and rejection payload families finish splitting.",
            duplicationExemptionReason =
                "The ledger-plan JSON aggregate intentionally repeats machine-contract record structure until payload families move into separate owners.",
        ),
        reviewedJavaSourceSurface(
            projectPath = FinGrindProjectPaths.CLI,
            relativePath = "src/main/java/dev/erst/fingrind/cli/json/CliRejectionJsonModels.java",
            owner = "cli-json-contract",
            reason =
                "The rejection JSON aggregate remains one explicit machine-contract namespace, with the public rejection envelope vocabulary grouped in one place.",
            splitTrigger =
                "Split by rejection family before adding another unrelated rejection namespace or serializer concern.",
            roleName = "cli-rejection-json-aggregate",
            physicalLines = 413,
            logicalLines = 366,
            imports = 6,
            nestedTypes = 50,
            methodsPerTopLevelType = 18,
            fieldsPerTopLevelType = 18,
            switchArmsPerMethod = 10,
            methodLineSpan = 60,
            methodParameters = 6,
            methodDecisionPoints = 14,
            approval =
                reviewedApproval(
                    physicalLines = 412,
                    logicalLines = 359,
                    imports = 6,
                    nestedTypes = 43,
                    methodsPerTopLevelType = 0,
                    fieldsPerTopLevelType = 0,
                    switchArmsPerMethod = 0,
                    methodLineSpan = 9,
                    methodParameters = 0,
                    methodDecisionPoints = 1,
                    expiresOn = reviewedExpiry("2026-08-12"),
                ),
            budgetVarianceReason =
                "The rejection JSON aggregate exceeds the default CLI JSON budget until rejection families move into separate owners.",
            duplicationExemptionReason =
                "The rejection JSON aggregate intentionally repeats machine-contract record structure until rejection payload families move into separate owners.",
        ),
        reviewedJavaSourceSurface(
            projectPath = FinGrindProjectPaths.CLI,
            relativePath = "src/main/java/dev/erst/fingrind/cli/json/CliReportJsonModels.java",
            owner = "cli-json-contract",
            reason =
                "Report JSON remains one machine-contract family, but it is reviewed as a frozen large surface instead of being treated as a normal-size DTO file.",
            splitTrigger =
                "Split by report family before adding another statement or export payload namespace.",
            roleName = "cli-report-json-aggregate",
            physicalLines = 333,
            logicalLines = 314,
            imports = 8,
            nestedTypes = 40,
            methodsPerTopLevelType = 18,
            fieldsPerTopLevelType = 18,
            switchArmsPerMethod = 10,
            methodLineSpan = 60,
            methodParameters = 6,
            methodDecisionPoints = 14,
            approval =
                reviewedApproval(
                    physicalLines = 332,
                    logicalLines = 313,
                    imports = 8,
                    nestedTypes = 15,
                    methodsPerTopLevelType = 0,
                    fieldsPerTopLevelType = 0,
                    switchArmsPerMethod = 0,
                    methodLineSpan = 15,
                    methodParameters = 0,
                    methodDecisionPoints = 0,
                    expiresOn = reviewedExpiry("2026-08-19"),
                ),
            budgetVarianceReason =
                "The report JSON aggregate exceeds the default CLI JSON budget until statement families move into separate owners.",
            duplicationExemptionReason =
                "The report JSON aggregate intentionally repeats machine-contract record structure until report payload families move into separate owners.",
        ),
    )
