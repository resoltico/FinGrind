package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.inputStream
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class StructuralGovernanceContractTest {
    private val repositoryRoot = Path.of("").toAbsolutePath().normalize().parent.parent
    private val changelog = repositoryRoot.resolve("CHANGELOG.md")
    private val productionRuleset = repositoryRoot.resolve("gradle/pmd/ruleset.xml")
    private val testRuleset = repositoryRoot.resolve("gradle/pmd/test-ruleset.xml")

    @Test
    fun productionRuleset_enforcesAdvertisedStructuralPmdFamilies() {
        val explicitRules = explicitRuleRefs(productionRuleset)
        assertTrue(
            "category/java/design.xml/GodClass" in explicitRules,
            "Production PMD ruleset must enforce GodClass explicitly.",
        )
        assertTrue(
            "category/java/design.xml/TooManyMethods" in explicitRules,
            "Production PMD ruleset must enforce TooManyMethods explicitly.",
        )
        assertTrue(
            "category/java/design.xml/CyclomaticComplexity" in explicitRules,
            "Production PMD ruleset must enforce CyclomaticComplexity explicitly.",
        )
        assertTrue(
            "category/java/design.xml/CognitiveComplexity" in explicitRules,
            "Production PMD ruleset must enforce CognitiveComplexity explicitly.",
        )
        assertTrue(
            "category/java/design.xml/CouplingBetweenObjects" in explicitRules,
            "Production PMD ruleset must enforce CouplingBetweenObjects explicitly.",
        )
    }

    @Test
    fun testRuleset_keepsMethodCountRelaxedWhileExplicitlyReAddingOtherStructuralRules() {
        val explicitRules = explicitRuleRefs(testRuleset)
        val excludedRules = excludedRuleNames(testRuleset)
        assertTrue(
            "category/java/design.xml/GodClass" in explicitRules,
            "Test PMD ruleset must keep GodClass enforced explicitly.",
        )
        assertTrue(
            "category/java/design.xml/CouplingBetweenObjects" in explicitRules,
            "Test PMD ruleset must keep CouplingBetweenObjects enforced explicitly.",
        )
        assertTrue(
            "category/java/design.xml/CyclomaticComplexity" in explicitRules,
            "Test PMD ruleset must keep CyclomaticComplexity enforced explicitly.",
        )
        assertTrue(
            "category/java/design.xml/CognitiveComplexity" in explicitRules,
            "Test PMD ruleset must keep CognitiveComplexity enforced explicitly.",
        )
        assertTrue(
            "TooManyMethods" in excludedRules,
            "Test PMD ruleset may relax TooManyMethods explicitly for test suites.",
        )
    }

    @Test
    fun changelogStructuralGovernanceClaim_matchesLiveGateOwners() {
        val changelogText = changelog.readText()
        assertTrue(
            "PMD now fails god-class, method-count, complexity, and coupling violations" in changelogText,
            "Changelog must describe the live PMD structural-governance claim precisely.",
        )
        assertTrue(
            "source-shape budgets fail oversized Java files" in changelogText,
            "Changelog must describe the live source-shape structural-governance owner.",
        )
        assertTrue(
            "duplication checks reject large repeated" in changelogText,
            "Changelog must describe the live duplication structural-governance owner.",
        )
        assertTrue(
            "reviewed structural inventory now owns every near-ceiling production Java surface" in
                changelogText,
            "Changelog must describe the reviewed structural inventory owner.",
        )
        assertTrue(
            "Python support scripts and SQLite schema SQL now sit under the same structural governance surface" in
                changelogText,
            "Changelog must describe the broadened non-Java structural-governance owner.",
        )
    }

    private fun explicitRuleRefs(path: Path): Set<String> =
        parseRuleset(path)
            .getElementsByTagName("rule")
            .let { rules ->
                buildSet {
                    for (index in 0 until rules.length) {
                        val node = rules.item(index)
                        val ref = node.attributes?.getNamedItem("ref")?.nodeValue ?: continue
                        if (!ref.endsWith(".xml")) {
                            add(ref)
                        }
                    }
                }
            }

    private fun excludedRuleNames(path: Path): Set<String> =
        parseRuleset(path)
            .getElementsByTagName("exclude")
            .let { excludes ->
                buildSet {
                    for (index in 0 until excludes.length) {
                        val node = excludes.item(index)
                        val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
                        add(name)
                    }
                }
            }

    private fun parseRuleset(path: Path) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.inputStream())
}
