package dev.erst.fingrind.buildlogic

internal val reviewedJavaContractSourceSurfaces =
    listOf(
        reviewedJavaSourceSurface(
            projectPath = FinGrindProjectPaths.CONTRACT,
            relativePath = "src/main/java/dev/erst/fingrind/contract/bookkeeping/RejectionNarrative.java",
            owner = "contract-bookkeeping",
            reason =
                "The published bookkeeping rejection narrative remains one explicit language catalog for stable public refusal text.",
            splitTrigger =
                "Split by rejection family before adding another unrelated narrative vocabulary branch.",
            roleName = "bookkeeping-rejection-narrative",
            approval =
                reviewedApproval(
                    physicalLines = 230,
                    logicalLines = 178,
                    imports = 4,
                    nestedTypes = 0,
                    methodsPerTopLevelType = 5,
                    fieldsPerTopLevelType = 0,
                    switchArmsPerMethod = 18,
                    methodLineSpan = 97,
                    methodParameters = 1,
                    methodDecisionPoints = 2,
                    expiresOn = reviewedExpiry("2026-07-15"),
                ),
            budgetVarianceReason =
                "The published rejection-language catalog exceeds the default exported seam budget until the named rejection families are split.",
        ),
    )
