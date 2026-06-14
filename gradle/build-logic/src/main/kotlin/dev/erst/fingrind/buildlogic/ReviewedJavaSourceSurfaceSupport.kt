package dev.erst.fingrind.buildlogic

import java.time.LocalDate

internal fun reviewedExpiry(isoDate: String): LocalDate = LocalDate.parse(isoDate)

internal fun reviewedApproval(
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
) = ReviewedJavaSourceApproval(
    approvedShape =
        JavaSourceShapeMetrics(
            physicalLineCount = physicalLines,
            logicalLineCount = logicalLines,
            importCount = imports,
            nestedTypeCount = nestedTypes,
            maxMethodsPerTopLevelType = methodsPerTopLevelType,
            maxFieldsPerTopLevelType = fieldsPerTopLevelType,
            maxSwitchArmsPerMethod = switchArmsPerMethod,
            maxMethodLineSpan = methodLineSpan,
            maxMethodParameters = methodParameters,
            maxMethodDecisionPoints = methodDecisionPoints,
        ),
    expiresOn = expiresOn,
)

internal fun reviewedJavaSourceSurface(
    projectPath: String,
    relativePath: String,
    owner: String,
    reason: String,
    splitTrigger: String,
    roleName: String,
    approval: ReviewedJavaSourceApproval,
    budgetVarianceReason: String? = null,
    duplicationExemptionReason: String? = null,
) = ReviewedJavaSourceSurface(
    projectPath = projectPath,
    relativePath = relativePath,
    owner = owner,
    reason = reason,
    splitTrigger = splitTrigger,
    reviewedRoleName = roleName,
    budgetVarianceReason = budgetVarianceReason,
    duplicationExemptionReason = duplicationExemptionReason,
    approval = approval,
)

internal val reviewedSurfaceEntries =
    reviewedProductionSourceSurfaces + reviewedTestSourceSurfaces
