package dev.erst.fingrind.buildlogic

internal val productionMainBudget =
    JavaSourceShapeBudget(
        roleName = "production-main",
        maxPhysicalLines = 350,
        maxLogicalLines = 335,
        maxImports = 44,
        maxNestedTypes = 10,
        maxMethodsPerTopLevelType = 20,
        maxFieldsPerTopLevelType = 10,
        maxSwitchArmsPerMethod = 12,
        maxMethodLineSpan = 130,
        maxMethodParameters = 8,
        maxMethodDecisionPoints = 20,
    )

internal val exportedPublicSeamBudget =
    JavaSourceShapeBudget(
        roleName = "exported-public-seam",
        maxPhysicalLines = 340,
        maxLogicalLines = 335,
        maxImports = 44,
        maxNestedTypes = 18,
        maxMethodsPerTopLevelType = 20,
        maxFieldsPerTopLevelType = 12,
        maxSwitchArmsPerMethod = 14,
        maxMethodLineSpan = 130,
        maxMethodParameters = 12,
        maxMethodDecisionPoints = 22,
    )

internal val testBudget =
    JavaSourceShapeBudget(
        roleName = "test-suite",
        maxPhysicalLines = 980,
        maxLogicalLines = 930,
        maxImports = 80,
        maxNestedTypes = 40,
        maxMethodsPerTopLevelType = 38,
        maxFieldsPerTopLevelType = 28,
        maxSwitchArmsPerMethod = 18,
        maxMethodLineSpan = 240,
        maxMethodParameters = 10,
        maxMethodDecisionPoints = 30,
    )

internal val cliJsonFamilyBudget =
    JavaSourceShapeBudget(
        roleName = "cli-json-family",
        maxPhysicalLines = 280,
        maxLogicalLines = 260,
        maxImports = 16,
        maxNestedTypes = 16,
        maxMethodsPerTopLevelType = 12,
        maxFieldsPerTopLevelType = 14,
        maxSwitchArmsPerMethod = 10,
        maxMethodLineSpan = 70,
        maxMethodParameters = 12,
        maxMethodDecisionPoints = 14,
    )

internal val testFixturesBudget =
    JavaSourceShapeBudget(
        roleName = "test-fixtures",
        maxPhysicalLines = 900,
        maxLogicalLines = 780,
        maxImports = 56,
        maxNestedTypes = 20,
        maxMethodsPerTopLevelType = 28,
        maxFieldsPerTopLevelType = 18,
        maxSwitchArmsPerMethod = 16,
        maxMethodLineSpan = 170,
        maxMethodParameters = 10,
        maxMethodDecisionPoints = 24,
    )

internal val fuzzBudget =
    JavaSourceShapeBudget(
        roleName = "fuzz-suite",
        maxPhysicalLines = 900,
        maxLogicalLines = 780,
        maxImports = 56,
        maxNestedTypes = 20,
        maxMethodsPerTopLevelType = 22,
        maxFieldsPerTopLevelType = 14,
        maxSwitchArmsPerMethod = 16,
        maxMethodLineSpan = 180,
        maxMethodParameters = 10,
        maxMethodDecisionPoints = 26,
    )
