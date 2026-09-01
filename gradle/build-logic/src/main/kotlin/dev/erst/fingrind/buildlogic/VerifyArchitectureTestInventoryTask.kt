package dev.erst.fingrind.buildlogic

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Verifies that every declared architecture rule is discovered by the selected test engines. */
abstract class VerifyArchitectureTestInventoryTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val ruleSource: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inventoryTestSource: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testResultsDirectory: DirectoryProperty

    @TaskAction
    fun verifyInventory() {
        val expectedArchRules = annotationCount(ruleSource.get().asFile, "@ArchTest")
        val expectedJupiterTests = annotationCount(inventoryTestSource.get().asFile, "@Test")
        val actualTestResults =
            testResultsDirectory
                .get()
                .asFile
                .walkTopDown()
                .filter { it.isFile && it.name.startsWith("TEST-") && it.extension == "xml" }
                .sumOf(::testCount)
        val expectedTestResults = expectedArchRules + expectedJupiterTests
        check(actualTestResults == expectedTestResults) {
            "Architecture verification discovered $actualTestResults tests but declares " +
                "$expectedArchRules @ArchTest rules and $expectedJupiterTests JUnit checks."
        }
    }

    private fun annotationCount(source: File, annotation: String): Int =
        source.useLines { lines -> lines.count { line -> line.trim() == annotation } }

    private fun testCount(report: File): Int {
        val document =
            report.inputStream().use { input ->
                DocumentBuilderFactory.newInstance()
                    .apply {
                        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                        setFeature("http://xml.org/sax/features/external-general-entities", false)
                        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                    }.newDocumentBuilder()
                    .parse(input)
            }
        return document.documentElement.getAttribute("tests").toInt()
    }
}
