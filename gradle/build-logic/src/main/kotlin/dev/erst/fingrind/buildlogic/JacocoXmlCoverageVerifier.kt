package dev.erst.fingrind.buildlogic

import java.io.File
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

internal object JacocoXmlCoverageVerifier {
    private const val reportElementName = "report"
    private const val counterElementName = "counter"

    data class CoverageSummary(
        val missedLines: Int,
        val coveredLines: Int,
        val missedBranches: Int,
        val coveredBranches: Int,
    )

    fun verifyReport(reportFile: File) {
        val summary = loadSummary(reportFile)
        if (summary.missedLines == 0 && summary.missedBranches == 0) {
            return
        }
        throw IllegalStateException(
            "JaCoCo coverage verification failed for ${reportFile.absolutePath}: " +
                "${summary.missedLines} missed line(s), ${summary.missedBranches} missed branch(es).",
        )
    }

    fun loadSummary(reportFile: File): CoverageSummary {
        require(reportFile.isFile) {
            "Expected JaCoCo XML report at ${reportFile.absolutePath}."
        }
        return reportFile.inputStream().use(::parseSummary)
    }

    fun parseSummary(inputStream: InputStream): CoverageSummary {
        val document = parseDocument(inputStream)
        val root = document.documentElement
            ?: throw IllegalStateException("JaCoCo XML report is missing its root element.")
        check(root.tagName == reportElementName) {
            "Expected JaCoCo XML report root <$reportElementName> but found <${root.tagName}>."
        }
        return CoverageSummary(
            missedLines = requireCounter(root, "LINE", "missed"),
            coveredLines = requireCounter(root, "LINE", "covered"),
            missedBranches = requireCounter(root, "BRANCH", "missed"),
            coveredBranches = requireCounter(root, "BRANCH", "covered"),
        )
    }

    private fun parseDocument(inputStream: InputStream): Document =
        newDocumentBuilderFactory().newDocumentBuilder().parse(inputStream)

    private fun newDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isXIncludeAware = false
            setExpandEntityReferences(false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }

    private fun requireCounter(root: Element, type: String, attributeName: String): Int =
        directCounterElements(root)
            .firstOrNull { it.getAttribute("type") == type }
            ?.getAttribute(attributeName)
            ?.toInt()
            ?: throw IllegalStateException("JaCoCo XML report is missing the $type counter.")

    private fun directCounterElements(root: Element): Sequence<Element> =
        sequence {
            val children = root.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index) as? Element ?: continue
                if (child.tagName == counterElementName) {
                    yield(child)
                }
            }
        }
}
