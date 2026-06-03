package dev.erst.fingrind.buildlogic

internal val reviewedJavaSqliteAndPdfTestSourceSurfaces =
    listOf(
        reviewedJavaSourceSurface(
            relativePath =
                "src/test/java/dev/erst/fingrind/sqlite/SqliteBookSchemaContractTest.java",
            owner = "sqlite-schema-tests",
            reason =
                "The canonical SQLite schema contract suite intentionally proves one large schema surface against one executable fixture matrix.",
            splitTrigger =
                "Split by schema concern before adding another independent schema contract family.",
            roleName = "sqlite-schema-contract-test",
            physicalLines = 1304,
            logicalLines = 1254,
            imports = 26,
            nestedTypes = 48,
            methodsPerTopLevelType = 44,
            fieldsPerTopLevelType = 32,
            switchArmsPerMethod = 20,
            methodLineSpan = 260,
            methodParameters = 10,
            methodDecisionPoints = 32,
        ),
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
        ),
        reviewedJavaSourceSurface(
            relativePath = "src/test/java/dev/erst/fingrind/report/pdf/PdfReportServiceTest.java",
            owner = "pdf-report-tests",
            reason =
                "The PDF report service suite remains one reviewed executable matrix over the public PDF rendering surface.",
            splitTrigger =
                "Split by report family or layout concern before adding another broad PDF rendering branch.",
            roleName = "pdf-report-service-test",
            physicalLines = 1001,
            logicalLines = 950,
            imports = 78,
            nestedTypes = 48,
            methodsPerTopLevelType = 40,
            fieldsPerTopLevelType = 32,
            switchArmsPerMethod = 20,
            methodLineSpan = 360,
            methodParameters = 10,
            methodDecisionPoints = 32,
        ),
    )
