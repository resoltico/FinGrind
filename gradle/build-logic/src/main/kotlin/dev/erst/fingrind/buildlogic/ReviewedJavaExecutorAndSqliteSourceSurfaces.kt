package dev.erst.fingrind.buildlogic

internal val reviewedJavaExecutorAndSqliteSourceSurfaces =
    listOf(
        reviewedJavaSourceSurface(
            projectPath = FinGrindProjectPaths.EXECUTOR,
            relativePath = "src/main/java/dev/erst/fingrind/executor/bookkeeping/PeriodResultTransferPlanner.java",
            owner = "executor-bookkeeping",
            reason =
                "Period-result transfer planning remains one reviewed bookkeeping-policy owner instead of drifting across adjacent workflow helpers.",
            splitTrigger =
                "Split by close-horizon validation, transfer readiness, and posting-shape derivation before adding another planning concern.",
            roleName = "period-result-transfer-planner",
            physicalLines = 404,
            logicalLines = 379,
            imports = 44,
            nestedTypes = 6,
            methodsPerTopLevelType = 18,
            fieldsPerTopLevelType = 10,
            switchArmsPerMethod = 10,
            methodLineSpan = 100,
            methodParameters = 10,
            methodDecisionPoints = 20,
            approval =
                reviewedApproval(
                    physicalLines = 403,
                    logicalLines = 362,
                    imports = 44,
                    nestedTypes = 4,
                    methodsPerTopLevelType = 10,
                    fieldsPerTopLevelType = 1,
                    switchArmsPerMethod = 2,
                    methodLineSpan = 52,
                    methodParameters = 6,
                    methodDecisionPoints = 7,
                    expiresOn = reviewedExpiry("2026-07-10"),
                ),
            budgetVarianceReason =
                "The close-planning owner exceeds the default production budget until readiness, horizon, and posting-shape concerns are split further.",
        ),
        reviewedJavaSourceSurface(
            projectPath = FinGrindProjectPaths.EXECUTOR,
            relativePath = "src/main/java/dev/erst/fingrind/executor/workflow/BookWorkflowExecutionService.java",
            owner = "executor-workflow",
            reason =
                "The workflow execution service intentionally aggregates the public workflow dispatch seam, but it is reviewed as a frozen large surface rather than being allowed to grow implicitly.",
            splitTrigger =
                "Split by workflow family before adding another command decision branch or another execution collaborator cluster.",
            roleName = "workflow-execution-service",
            physicalLines = 391,
            logicalLines = 363,
            imports = 13,
            nestedTypes = 4,
            methodsPerTopLevelType = 18,
            fieldsPerTopLevelType = 10,
            switchArmsPerMethod = 10,
            methodLineSpan = 85,
            methodParameters = 10,
            methodDecisionPoints = 18,
            approval =
                reviewedApproval(
                    physicalLines = 390,
                    logicalLines = 358,
                    imports = 13,
                    nestedTypes = 2,
                    methodsPerTopLevelType = 15,
                    fieldsPerTopLevelType = 3,
                    switchArmsPerMethod = 2,
                    methodLineSpan = 30,
                    methodParameters = 8,
                    methodDecisionPoints = 3,
                    expiresOn = reviewedExpiry("2026-07-17"),
                ),
            budgetVarianceReason =
                "The workflow execution owner exceeds the default production budget until command-family dispatch seams are split further.",
        ),
        reviewedJavaSourceSurface(
            projectPath = FinGrindProjectPaths.SQLITE,
            relativePath = "src/main/java/dev/erst/fingrind/sqlite/SqlitePostingSqlLiterals.java",
            owner = "sqlite-posting-read-write",
            reason =
                "The posting SQL literal catalog is a reviewed data surface whose behavioral query assembly lives in separate owners.",
            splitTrigger =
                "Split by query family or generate the catalog before adding another unrelated statement cluster or behavioral helper.",
            roleName = "sqlite-posting-sql-catalog",
            physicalLines = 623,
            logicalLines = 559,
            imports = 0,
            nestedTypes = 2,
            methodsPerTopLevelType = 4,
            fieldsPerTopLevelType = 0,
            switchArmsPerMethod = 2,
            methodLineSpan = 20,
            methodParameters = 0,
            methodDecisionPoints = 2,
            approval =
                reviewedApproval(
                    physicalLines = 613,
                    logicalLines = 160,
                    imports = 0,
                    nestedTypes = 0,
                    methodsPerTopLevelType = 1,
                    fieldsPerTopLevelType = 0,
                    switchArmsPerMethod = 0,
                    methodLineSpan = 1,
                    methodParameters = 0,
                    methodDecisionPoints = 0,
                    expiresOn = reviewedExpiry("2026-07-24"),
                ),
            budgetVarianceReason =
                "The SQLite posting SQL catalog exceeds the default production budget until query families are generated or split into narrower catalogs.",
        ),
        reviewedJavaSourceSurface(
            projectPath = FinGrindProjectPaths.SQLITE,
            relativePath = "src/main/java/dev/erst/fingrind/sqlite/internal/SqliteNativeCalls.java",
            owner = "sqlite-native-bridge",
            reason =
                "The typed SQLite native-call interface table remains one explicit reviewed bridge catalog while lifecycle and error behavior live elsewhere.",
            splitTrigger =
                "Split the native-call vocabulary by lifecycle, statement, and metadata call families before adding another interface cluster.",
            roleName = "sqlite-native-call-table",
            physicalLines = 158,
            logicalLines = 137,
            imports = 1,
            nestedTypes = 20,
            methodsPerTopLevelType = 18,
            fieldsPerTopLevelType = 0,
            switchArmsPerMethod = 2,
            methodLineSpan = 20,
            methodParameters = 12,
            methodDecisionPoints = 2,
            approval =
                reviewedApproval(
                    physicalLines = 157,
                    logicalLines = 98,
                    imports = 1,
                    nestedTypes = 19,
                    methodsPerTopLevelType = 0,
                    fieldsPerTopLevelType = 0,
                    switchArmsPerMethod = 0,
                    methodLineSpan = 6,
                    methodParameters = 5,
                    methodDecisionPoints = 0,
                    expiresOn = reviewedExpiry("2026-08-07"),
                ),
            budgetVarianceReason =
                "The typed native-call table exceeds the default production budget until lifecycle, statement, and metadata call families are split further.",
        ),
    )
