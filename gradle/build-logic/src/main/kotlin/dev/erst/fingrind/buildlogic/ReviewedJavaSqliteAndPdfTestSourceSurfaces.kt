package dev.erst.fingrind.buildlogic

internal val reviewedJavaSqliteAndPdfTestSourceSurfaces =
    listOf(
        reviewedJavaSourceSurface(
            relativePath =
                "src/test/java/dev/erst/fingrind/sqlite/SqliteProtectedBookMaintenanceStoreCoverageTest.java",
            owner = "sqlite-maintenance-tests",
            reason =
                "The protected-book maintenance store coverage matrix intentionally spans one broad persistence lifecycle surface and is reviewed as such.",
            splitTrigger =
                "Split by backup, restore, and rollback persistence scenarios before adding another maintenance branch family.",
            roleName = "sqlite-maintenance-store-coverage-test",
            physicalLines = 1027,
            logicalLines = 937,
            imports = 40,
            nestedTypes = 48,
            methodsPerTopLevelType = 42,
            fieldsPerTopLevelType = 32,
            switchArmsPerMethod = 20,
            methodLineSpan = 260,
            methodParameters = 10,
            methodDecisionPoints = 32,
            expiresOn = reviewedExpiry(7, 23),
            budgetVarianceReason =
                "The maintenance store matrix exceeds the default test-suite budget until backup, restore, and rollback persistence scenarios are split further.",
        ),
        reviewedJavaSourceSurface(
            relativePath = "src/test/java/dev/erst/fingrind/sqlite/SqliteRuntimeProbeStatusTest.java",
            owner = "sqlite-runtime-tests",
            reason =
                "The runtime probe status suite intentionally covers the public runtime-status lattice in one reviewed matrix.",
            splitTrigger =
                "Split by runtime state family before adding another probe-status branch.",
            roleName = "sqlite-runtime-probe-test",
            physicalLines = 1003,
            logicalLines = 962,
            imports = 23,
            nestedTypes = 48,
            methodsPerTopLevelType = 40,
            fieldsPerTopLevelType = 32,
            switchArmsPerMethod = 20,
            methodLineSpan = 380,
            methodParameters = 10,
            methodDecisionPoints = 32,
            expiresOn = reviewedExpiry(7, 30),
            budgetVarianceReason =
                "The runtime probe matrix exceeds the default test-suite budget until runtime-state families are split further.",
        ),
    )
