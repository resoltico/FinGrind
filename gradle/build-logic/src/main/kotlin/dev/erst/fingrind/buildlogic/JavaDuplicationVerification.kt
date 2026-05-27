package dev.erst.fingrind.buildlogic

import java.io.File
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import net.sourceforge.pmd.cpd.CPDConfiguration
import net.sourceforge.pmd.cpd.CPDReport
import net.sourceforge.pmd.cpd.CpdAnalysis
import net.sourceforge.pmd.lang.LanguageRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

internal fun Project.registerJavaSourceDuplicationTask() =
    if ("verifyJavaSourceDuplication" in tasks.names) {
        tasks.named<VerifyJavaSourceDuplicationTask>("verifyJavaSourceDuplication")
    } else {
        tasks.register<VerifyJavaSourceDuplicationTask>("verifyJavaSourceDuplication") {
            group = "verification"
            description =
                "Fails the build when Java source files contain duplicate token sequences."
            sourceRoots.from(
                listOf(
                    file("src/main/java"),
                ).filter(File::isDirectory),
            )
            reportFile.set(layout.buildDirectory.file("reports/pmd/cpd.xml"))
        }
    }

abstract class VerifyJavaSourceDuplicationTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoots: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val existingSourceRoots = sourceRoots.files.filter(File::isDirectory).sortedBy { it.path }
        val report = reportFile.get().asFile
        if (existingSourceRoots.isEmpty()) {
            report.delete()
            return
        }
        report.parentFile.mkdirs()
        if (report.exists()) {
            report.delete()
        }
        val configuration = CPDConfiguration(LanguageRegistry.CPD)
        configuration.setSourceEncoding(StandardCharsets.UTF_8)
        configuration.minimumTileSize = 200
        configuration.isIgnoreIdentifiers = true
        configuration.isIgnoreLiterals = true
        configuration.isIgnoreAnnotations = true
        configuration.isSkipDuplicates = true
        configuration.rendererName = "xml"
        var duplicationCount = 0
        val xmlReport =
            CpdAnalysis.create(configuration).use { analysis ->
                existingSourceRoots.forEach { sourceRoot ->
                    sourceRoot
                        .walkTopDown()
                        .filter(File::isFile)
                        .filter { it.extension == "java" }
                        .filter { translationHeavyClassNamePattern.matches(it.name) }
                        .forEach { sourceFile ->
                            analysis.files().addFile(sourceFile.toPath())
                        }
                }
                val writer = StringWriter()
                analysis.performAnalysis { cpdReport ->
                    duplicationCount = duplicationCountFor(cpdReport)
                    configuration.getCPDReportRenderer().render(cpdReport, writer)
                }
                writer.toString()
            }
        report.writeText(xmlReport, StandardCharsets.UTF_8)
        if (!report.isFile) {
            throw GradleException(
                "PMD CPD did not produce a report at ${report.invariantSeparatorsPath()}.",
            )
        }
        if (duplicationCount > 0) {
            throw GradleException(
                "FinGrind Java duplication violations: $duplicationCount translation-heavy duplicate blocks exceed 200 tokens. See ${report.invariantSeparatorsPath()}.",
            )
        }
    }

    private fun duplicationCountFor(report: CPDReport): Int = report.getMatches().size
}
