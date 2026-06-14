package dev.erst.fingrind.buildlogic

internal val reviewedJavaSqliteAndPdfTestSourceSurfaces =
    listOf(
        reviewedJavaSourceSurface(
            projectPath = FinGrindProjectPaths.SQLITE,
            relativePath = "src/test/java/dev/erst/fingrind/sqlite/SqliteRuntimeProbeStatusTest.java",
            owner = "sqlite-runtime-tests",
            reason =
                "The runtime probe status suite intentionally covers the public runtime-status lattice in one reviewed matrix.",
            splitTrigger =
                "Split by runtime state family before adding another probe-status branch.",
            roleName = "sqlite-runtime-probe-test",
            approval =
                reviewedApproval(
                    physicalLines = 962,
                    logicalLines = 925,
                    imports = 23,
                    nestedTypes = 0,
                    methodsPerTopLevelType = 26,
                    fieldsPerTopLevelType = 0,
                    switchArmsPerMethod = 0,
                    methodLineSpan = 357,
                    methodParameters = 9,
                    methodDecisionPoints = 3,
                    expiresOn = reviewedExpiry("2026-07-30"),
                ),
            budgetVarianceReason =
                "The runtime probe matrix exceeds the default test-suite budget until runtime-state families are split further.",
        ),
    )
