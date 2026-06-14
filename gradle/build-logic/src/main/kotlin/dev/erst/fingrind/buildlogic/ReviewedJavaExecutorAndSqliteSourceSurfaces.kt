package dev.erst.fingrind.buildlogic

internal val reviewedJavaExecutorAndSqliteSourceSurfaces =
    listOf(
        reviewedJavaSourceSurface(
            projectPath = FinGrindProjectPaths.SQLITE,
            relativePath = "src/main/java/dev/erst/fingrind/sqlite/internal/SqliteNativeCalls.java",
            owner = "sqlite-native-bridge",
            reason =
                "The typed SQLite native-call interface table remains one explicit reviewed bridge catalog while lifecycle and error behavior live elsewhere.",
            splitTrigger =
                "Split the native-call vocabulary by lifecycle, statement, and metadata call families before adding another interface cluster.",
            roleName = "sqlite-native-call-table",
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
