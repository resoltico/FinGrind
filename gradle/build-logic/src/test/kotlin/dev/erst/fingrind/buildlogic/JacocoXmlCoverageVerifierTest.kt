package dev.erst.fingrind.buildlogic

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JacocoXmlCoverageVerifierTest {
    @Test
    fun parseSummary_readsZeroMissReportCounters() {
        val summary =
            JacocoXmlCoverageVerifier.parseSummary(
                reportInputStream(
                    """
                    <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
                    <report name="sqlite">
                      <counter type="LINE" missed="0" covered="42"/>
                      <counter type="BRANCH" missed="0" covered="17"/>
                    </report>
                    """.trimIndent(),
                ),
            )

        assertEquals(0, summary.missedLines)
        assertEquals(42, summary.coveredLines)
        assertEquals(0, summary.missedBranches)
        assertEquals(17, summary.coveredBranches)
    }

    @Test
    fun verifyReport_rejectsMissedCoverage() {
        val reportFile = Files.createTempFile("jacoco-coverage", ".xml").toFile()
        reportFile.writeText(
            """
            <report name="sqlite">
              <counter type="LINE" missed="1" covered="41"/>
              <counter type="BRANCH" missed="2" covered="15"/>
            </report>
            """.trimIndent(),
        )

        val failure =
            assertFailsWith<IllegalStateException> {
                JacocoXmlCoverageVerifier.verifyReport(reportFile)
            }

        assertEquals(
            "JaCoCo coverage verification failed for ${reportFile.absolutePath}: 1 missed line(s), 2 missed branch(es).",
            failure.message,
        )
    }

    @Test
    fun loadSummary_rejectsMissingReportFile() {
        val missingReport = Files.createTempDirectory("jacoco-coverage-missing").resolve("report.xml").toFile()

        val failure =
            assertFailsWith<IllegalArgumentException> {
                JacocoXmlCoverageVerifier.loadSummary(missingReport)
            }

        assertEquals(
            "Expected JaCoCo XML report at ${missingReport.absolutePath}.",
            failure.message,
        )
    }

    @Test
    fun parseSummary_rejectsMissingBranchCounter() {
        val failure =
            assertFailsWith<IllegalStateException> {
                JacocoXmlCoverageVerifier.parseSummary(
                    reportInputStream(
                        """
                        <report name="sqlite">
                          <counter type="LINE" missed="0" covered="42"/>
                        </report>
                        """.trimIndent(),
                    ),
                )
            }

        assertEquals("JaCoCo XML report is missing the BRANCH counter.", failure.message)
    }

    @Test
    fun parseSummary_rejectsWrongRootElement() {
        val failure =
            assertFailsWith<IllegalStateException> {
                JacocoXmlCoverageVerifier.parseSummary(
                    reportInputStream(
                        """
                        <coverage>
                          <counter type="LINE" missed="0" covered="42"/>
                          <counter type="BRANCH" missed="0" covered="17"/>
                        </coverage>
                        """.trimIndent(),
                    ),
                )
            }

        assertEquals("Expected JaCoCo XML report root <report> but found <coverage>.", failure.message)
    }

    private fun reportInputStream(xml: String): ByteArrayInputStream =
        ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8))
}
