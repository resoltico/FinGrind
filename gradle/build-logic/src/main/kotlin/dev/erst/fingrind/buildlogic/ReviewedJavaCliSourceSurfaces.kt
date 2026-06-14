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
    )
