package dev.erst.fingrind.buildlogic

import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

internal fun Project.registerJavaSourceShapeTask() =
    if ("verifyJavaSourceShape" in tasks.names) {
        tasks.named<VerifyJavaSourceShapeTask>("verifyJavaSourceShape")
    } else {
        tasks.register<VerifyJavaSourceShapeTask>("verifyJavaSourceShape") {
            group = "verification"
            description =
                "Fails the build when Java source files exceed FinGrind's structural shape budgets."
            moduleName.set(this@registerJavaSourceShapeTask.name)
            projectDirectoryPath.set(projectDir.invariantSeparatorsPath())
            reportFile.set(
                layout.buildDirectory.file("reports/structural-governance/java-source-shape.tsv"),
            )
            auditMirrorFile.set(
                FinGrindFilesystemLayout.structuralGovernanceAuditFile(
                    project = this@registerJavaSourceShapeTask,
                    reportName = "java-source-shape.tsv",
                ),
            )
            sourceFiles.from(
                fileTree(projectDir) {
                    include("src/*/java/**/*.java")
                    exclude("**/build/**", "**/.gradle/**")
                },
            )
            outputs.upToDateWhen { false }
        }
    }

abstract class VerifyJavaSourceShapeTask : DefaultTask() {
    @get:Input
    abstract val moduleName: Property<String>

    @get:Input
    abstract val projectDirectoryPath: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:OutputFile
    abstract val auditMirrorFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val projectDirectory = File(projectDirectoryPath.get())
        val exportedPackages = JavaSourceStructuralContracts.exportedPackages(projectDirectory)
        val projectPath = moduleName.get()
        val currentDate = LocalDate.now()
        val rows =
            mutableListOf(
                "path\tdefaultRole\trole\treviewOwner\treviewExpiry\treviewBudgetVarianceReason\treviewDuplicationExemptionReason\tapprovedPhysical\tapprovedLogical\tapprovedImports\tapprovedNestedTypes\tapprovedMaxMethods\tapprovedMaxFields\tapprovedMaxSwitchArms\tapprovedMaxMethodLines\tapprovedMaxMethodParameters\tapprovedMaxMethodDecisionPoints\tphysical\tlogical\timports\tnestedTypes\tmaxMethods\tmaxFields\tmaxSwitchArms\tmaxMethodLines\tmaxMethodParameters\tmaxMethodDecisionPoints",
            )
        val violations = mutableListOf<String>()
        val existingRelativePaths = mutableSetOf<String>()
        sourceFiles.files
            .sortedBy { it.invariantSeparatorsPath() }
            .forEach { file ->
                val relativePath = file.displayPath(projectDirectory)
                if (file.name == "module-info.java" || file.name == "package-info.java") {
                    return@forEach
                }
                existingRelativePaths += relativePath
                val packageName = JavaSourceStructuralContracts.packageNameFor(file)
                val contract =
                    JavaSourceStructuralContracts.contractFor(
                        projectPath = projectPath,
                        relativePath = relativePath,
                        packageName = packageName,
                        exportedPackages = exportedPackages,
                    )
                val defaultBudget = contract.defaultBudget
                val metrics = JavaSourceShapeMetrics.measure(file)
                val reviewedSurface = contract.reviewedSurface
                val approval = reviewedSurface?.approval
                val approvedShape = approval?.approvedShape
                rows +=
                    listOf(
                            relativePath,
                            defaultBudget.roleName,
                            contract.activeRoleName,
                            reviewedSurface?.owner.orEmpty(),
                            approval?.expiresOn?.toString().orEmpty(),
                            reviewedSurface?.budgetVarianceReason.orEmpty(),
                            reviewedSurface?.duplicationExemptionReason.orEmpty(),
                            approvedShape?.physicalLineCount?.toString().orEmpty(),
                            approvedShape?.logicalLineCount?.toString().orEmpty(),
                            approvedShape?.importCount?.toString().orEmpty(),
                            approvedShape?.nestedTypeCount?.toString().orEmpty(),
                            approvedShape?.maxMethodsPerTopLevelType?.toString().orEmpty(),
                            approvedShape?.maxFieldsPerTopLevelType?.toString().orEmpty(),
                            approvedShape?.maxSwitchArmsPerMethod?.toString().orEmpty(),
                            approvedShape?.maxMethodLineSpan?.toString().orEmpty(),
                            approvedShape?.maxMethodParameters?.toString().orEmpty(),
                            approvedShape?.maxMethodDecisionPoints?.toString().orEmpty(),
                            metrics.physicalLineCount.toString(),
                            metrics.logicalLineCount.toString(),
                            metrics.importCount.toString(),
                            metrics.nestedTypeCount.toString(),
                            metrics.maxMethodsPerTopLevelType.toString(),
                            metrics.maxFieldsPerTopLevelType.toString(),
                            metrics.maxSwitchArmsPerMethod.toString(),
                            metrics.maxMethodLineSpan.toString(),
                            metrics.maxMethodParameters.toString(),
                            metrics.maxMethodDecisionPoints.toString(),
                        )
                        .joinToString("\t")
                if (reviewedSurface == null) {
                    violations += javaShapeViolations(relativePath, metrics, defaultBudget)
                } else {
                    violations += reviewedSurfaceDefinitionViolations(reviewedSurface, defaultBudget)
                    violations +=
                        reviewedSurfaceViolations(
                            relativePath = relativePath,
                            metrics = metrics,
                            reviewedSurface = reviewedSurface,
                            defaultBudget = defaultBudget,
                            currentDate = currentDate,
                        )
                }
            }
        violations +=
            JavaSourceStructuralContracts.missingReviewedSurfaceViolations(
                projectPath = projectPath,
                existingRelativePaths = existingRelativePaths,
            )
        val renderedReport =
            rows.joinToString(System.lineSeparator(), postfix = System.lineSeparator())
        val outputFile = reportFile.get().asFile
        val auditMirror = auditMirrorFile.get().asFile
        outputFile.parentFile.mkdirs()
        auditMirror.parentFile.mkdirs()
        outputFile.writeText(renderedReport, StandardCharsets.UTF_8)
        auditMirror.writeText(renderedReport, StandardCharsets.UTF_8)
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("FinGrind Java source-shape violations:")
                    violations.forEach(::appendLine)
                    appendLine(
                        "Structural inventory report: ${outputFile.invariantSeparatorsPath()}",
                    )
                    appendLine(
                        "Repo-local audit mirror: ${auditMirror.invariantSeparatorsPath()}",
                    )
                },
            )
        }
    }
}
