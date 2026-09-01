package dev.erst.fingrind.buildlogic

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element

/** Verifies that one completed PIT run supplies current, complete structured mutation evidence. */
abstract class VerifyMutationEvidenceTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reportDirectory: DirectoryProperty

    @get:Input
    abstract val expectedMutationCounts: MapProperty<String, Int>

    @get:Input
    abstract val projectPath: Property<String>

    @TaskAction
    fun verifyEvidence() {
        val mutations = File(reportDirectory.get().asFile, "mutations.xml")
        require(mutations.isFile) { "PIT did not produce ${mutations.absolutePath}." }

        val mutationElements = parseMutationElements(mutations)
        val statuses = mutationElements.groupingBy { it.getAttribute("status") }.eachCount()
        val nonKilled = statuses.filterKeys { it != "KILLED" }
        check(nonKilled.isEmpty()) {
            "PIT found non-killed mutations for ${projectPath.get()}: $nonKilled."
        }
        check(mutationElements.all { it.getAttribute("numberOfTestsRun").toInt() > 0 }) {
            "PIT reported a killed mutation without a covering test for ${projectPath.get()}."
        }

        val mutationCounts =
            mutationElements
                .groupingBy { element -> element.childText("mutatedClass") }
                .eachCount()
        check(mutationCounts == expectedMutationCounts.get()) {
            "PIT mutation inventory drifted for ${projectPath.get()}: expected " +
                expectedMutationCounts.get().toSortedMap() + " but found " + mutationCounts.toSortedMap() + "."
        }
    }

    private fun parseMutationElements(mutations: File): List<Element> {
        val document =
            mutations.inputStream().use { input ->
                newDocumentBuilderFactory().newDocumentBuilder().parse(input)
            }
        val root =
            requireNotNull(document.documentElement) {
                "PIT mutation XML has no root element."
            }
        check(root.tagName == "mutations") {
            "Expected PIT mutation root <mutations> but found <${root.tagName}>."
        }
        val elements = root.childNodes
        return buildList {
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                if (element.tagName == "mutation") {
                    add(element)
                }
            }
        }
    }

    private fun Element.childText(tagName: String): String {
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            if (child.tagName == tagName) {
                return child.textContent
            }
        }
        throw IllegalStateException("PIT mutation XML element <$tagName> is missing.")
    }

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
}
