package dev.erst.fingrind.buildlogic

import java.io.File
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
            projectPathValue.set(path)
            projectDirectoryPath.set(projectDir.invariantSeparatorsPath())
            reportFile.set(
                layout.buildDirectory.file("reports/structural-governance/java-source-shape.tsv"),
            )
            sourceFiles.from(
                fileTree(projectDir) {
                    include("src/*/java/**/*.java")
                    exclude("**/build/**", "**/.gradle/**")
                },
            )
        }
    }

abstract class VerifyJavaSourceShapeTask : DefaultTask() {
    @get:Input
    abstract val projectPathValue: Property<String>

    @get:Input
    abstract val projectDirectoryPath: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val projectDirectory = File(projectDirectoryPath.get())
        val exportedPackages = JavaSourceStructuralContracts.exportedPackages(projectDirectory)
        val rows =
            mutableListOf(
                "path\trole\treviewOwner\treviewExpiry\tapprovedPhysical\tapprovedLogical\tapprovedImports\tphysical\tlogical\timports\tnestedTypes\tmaxMethods\tmaxFields\tmaxSwitchArms\tmaxMethodLines\tmaxMethodParameters\tmaxMethodDecisionPoints",
            )
        val violations = mutableListOf<String>()
        sourceFiles.files
            .sortedBy { it.invariantSeparatorsPath() }
            .forEach { file ->
                val relativePath = file.displayPath(projectDirectory)
                if (file.name == "module-info.java" || file.name == "package-info.java") {
                    return@forEach
                }
                val packageName = JavaSourceStructuralContracts.packageNameFor(file)
                val contract =
                    JavaSourceStructuralContracts.contractFor(
                        relativePath = relativePath,
                        packageName = packageName,
                        exportedPackages = exportedPackages,
                    )
                val metrics = JavaSourceShapeMetrics.measure(file)
                val reviewedSurface = contract.reviewedSurface
                val approval = reviewedSurface?.approval
                rows +=
                    listOf(
                            relativePath,
                            contract.budget.roleName,
                            reviewedSurface?.owner.orEmpty(),
                            approval?.expiresOn?.toString().orEmpty(),
                            approval?.approvedPhysicalLines?.toString().orEmpty(),
                            approval?.approvedLogicalLines?.toString().orEmpty(),
                            approval?.approvedImports?.toString().orEmpty(),
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
                if (
                    "src/main/java/" in relativePath &&
                        forbiddenGenericClassNamePattern.matches(file.name)
                ) {
                    violations +=
                        "$relativePath: generic production class names like ${file.name} are forbidden; give the file one explicit domain or boundary role."
                }
                violations += javaShapeViolations(relativePath, metrics, contract.budget)
                if (reviewedSurface != null) {
                    violations += reviewedSurfaceViolations(relativePath, metrics, reviewedSurface)
                }
            }
        val renderedReport = rows.joinToString(System.lineSeparator(), postfix = System.lineSeparator())
        val outputFile = reportFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(renderedReport)
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("FinGrind Java source-shape violations:")
                    violations.forEach(::appendLine)
                    appendLine(
                        "Structural inventory report: ${outputFile.invariantSeparatorsPath()}",
                    )
                },
            )
        }
    }
}
