package dev.erst.fingrind.buildlogic

import java.time.LocalDate

internal data class JavaSourceShapeBudget(
    val roleName: String,
    val maxPhysicalLines: Int,
    val maxLogicalLines: Int,
    val maxImports: Int,
    val maxNestedTypes: Int,
    val maxMethodsPerTopLevelType: Int,
    val maxFieldsPerTopLevelType: Int,
    val maxSwitchArmsPerMethod: Int,
    val maxMethodLineSpan: Int,
    val maxMethodParameters: Int,
    val maxMethodDecisionPoints: Int,
)

internal data class ReviewedJavaSourceApproval(
    val approvedPhysicalLines: Int,
    val approvedLogicalLines: Int,
    val approvedImports: Int,
    val expiresOn: LocalDate,
)

internal data class ReviewedJavaSourceSurface(
    val relativePath: String,
    val owner: String,
    val reason: String,
    val splitTrigger: String,
    val budget: JavaSourceShapeBudget,
    val approval: ReviewedJavaSourceApproval,
)

internal data class JavaSourceStructuralContract(
    val budget: JavaSourceShapeBudget,
    val reviewedSurface: ReviewedJavaSourceSurface?,
)
