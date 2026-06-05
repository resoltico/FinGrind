package dev.erst.fingrind.buildlogic

import java.time.LocalDate

internal fun reviewedExpiry(month: Int, dayOfMonth: Int): LocalDate =
    LocalDate.of(2026, month, dayOfMonth)

internal fun reviewedApproval(
    physicalLines: Int,
    logicalLines: Int,
    imports: Int,
    expiresOn: LocalDate,
) = ReviewedJavaSourceApproval(
    approvedPhysicalLines = physicalLines,
    approvedLogicalLines = logicalLines,
    approvedImports = imports,
    expiresOn = expiresOn,
)

internal fun reviewedBudget(
    roleName: String,
    physicalLines: Int,
    logicalLines: Int,
    imports: Int,
    nestedTypes: Int,
    methodsPerTopLevelType: Int,
    fieldsPerTopLevelType: Int,
    switchArmsPerMethod: Int,
    methodLineSpan: Int,
    methodParameters: Int,
    methodDecisionPoints: Int,
) = JavaSourceShapeBudget(
    roleName = roleName,
    maxPhysicalLines = physicalLines,
    maxLogicalLines = logicalLines,
    maxImports = imports,
    maxNestedTypes = nestedTypes,
    maxMethodsPerTopLevelType = methodsPerTopLevelType,
    maxFieldsPerTopLevelType = fieldsPerTopLevelType,
    maxSwitchArmsPerMethod = switchArmsPerMethod,
    maxMethodLineSpan = methodLineSpan,
    maxMethodParameters = methodParameters,
    maxMethodDecisionPoints = methodDecisionPoints,
)

internal fun reviewedJavaSourceSurface(
    relativePath: String,
    owner: String,
    reason: String,
    splitTrigger: String,
    roleName: String,
    physicalLines: Int,
    logicalLines: Int,
    imports: Int,
    nestedTypes: Int,
    methodsPerTopLevelType: Int,
    fieldsPerTopLevelType: Int,
    switchArmsPerMethod: Int,
    methodLineSpan: Int,
    methodParameters: Int,
    methodDecisionPoints: Int,
    expiresOn: LocalDate,
    budgetVarianceReason: String? = null,
    duplicationExemptionReason: String? = null,
) = ReviewedJavaSourceSurface(
    relativePath = relativePath,
    owner = owner,
    reason = reason,
    splitTrigger = splitTrigger,
    budget =
        reviewedBudget(
            roleName = roleName,
            physicalLines = physicalLines,
            logicalLines = logicalLines,
            imports = imports,
            nestedTypes = nestedTypes,
            methodsPerTopLevelType = methodsPerTopLevelType,
            fieldsPerTopLevelType = fieldsPerTopLevelType,
            switchArmsPerMethod = switchArmsPerMethod,
            methodLineSpan = methodLineSpan,
            methodParameters = methodParameters,
            methodDecisionPoints = methodDecisionPoints,
        ),
    budgetVarianceReason = budgetVarianceReason,
    duplicationExemptionReason = duplicationExemptionReason,
    approval = reviewedApproval(physicalLines, logicalLines, imports, expiresOn),
)

internal val reviewedSurfaceEntries =
    reviewedProductionSourceSurfaces + reviewedTestSourceSurfaces
