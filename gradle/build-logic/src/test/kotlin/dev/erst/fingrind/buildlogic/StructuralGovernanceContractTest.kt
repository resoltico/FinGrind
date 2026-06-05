package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.inputStream
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue

class StructuralGovernanceContractTest {
    private val repositoryRoot = Path.of("").toAbsolutePath().normalize().parent.parent
    private val productionRuleset = repositoryRoot.resolve("gradle/pmd/ruleset.xml")
    private val testRuleset = repositoryRoot.resolve("gradle/pmd/test-ruleset.xml")
    private val structuralVerifier = repositoryRoot.resolve("scripts/verify-structural-governance.sh")
    private val stageOneGate = repositoryRoot.resolve("scripts/run-quality-gates.sh")
    private val structuralCli = repositoryRoot.resolve("scripts/structural_governance/cli.py")
    private val filesystemLayout =
        repositoryRoot.resolve(
            "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindFilesystemLayout.kt",
        )
    private val javaDuplicationVerifier =
        repositoryRoot.resolve(
            "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/JavaDuplicationVerification.kt",
        )

    @Test
    fun productionRuleset_enforcesAdvertisedStructuralPmdFamilies() {
        val document = parseRuleset(productionRuleset)
        val explicitRules = explicitRuleRefs(document)
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
        assertEquals("16", ruleProperty(document, "category/java/design.xml/TooManyMethods", "maxmethods"))
        assertEquals(
            "14",
            ruleProperty(document, "category/java/design.xml/CyclomaticComplexity", "methodReportLevel"),
        )
        assertEquals(
            "110",
            ruleProperty(document, "category/java/design.xml/CyclomaticComplexity", "classReportLevel"),
        )
        assertEquals("18", ruleProperty(document, "category/java/design.xml/CognitiveComplexity", "reportLevel"))
        assertEquals("50", ruleProperty(document, "category/java/design.xml/CouplingBetweenObjects", "threshold"))
    }

    @Test
    fun testRuleset_keepsMethodCountRelaxedWhileExplicitlyReAddingOtherStructuralRules() {
        val document = parseRuleset(testRuleset)
        val explicitRules = explicitRuleRefs(document)
        val excludedRules = excludedRuleNames(document)
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
        assertEquals(
            "16",
            ruleProperty(document, "category/java/design.xml/CyclomaticComplexity", "methodReportLevel"),
        )
        assertEquals(
            "130",
            ruleProperty(document, "category/java/design.xml/CyclomaticComplexity", "classReportLevel"),
        )
        assertEquals("20", ruleProperty(document, "category/java/design.xml/CognitiveComplexity", "reportLevel"))
        assertEquals("72", ruleProperty(document, "category/java/design.xml/CouplingBetweenObjects", "threshold"))
    }

    @Test
    fun structuralGovernanceVerifier_usageAndStageOneSurfacesStayAligned() {
        val supportedBlock =
            Regex("""SUPPORTED_SURFACES\s*=\s*\((.*?)\)""", setOf(RegexOption.DOT_MATCHES_ALL))
                .find(structuralCli.readText())
                ?.groupValues
                ?.get(1)
                ?: error("Missing SUPPORTED_SURFACES block.")
        val supportedSurfaces =
            Regex("\"([a-z-]+)\"")
                .findAll(supportedBlock)
                .map { it.groupValues[1] }
                .toSet()
        val helpText = structuralVerifier.readText()
        val stageOneText = stageOneGate.readText()
        assertEquals(
            setOf(
                "build-logic-kotlin",
                "gradle-kts",
                "markdown-docs",
                "shell-release",
                "python-support",
                "sqlite-sql",
            ),
            supportedSurfaces,
        )
        supportedSurfaces.forEach { surface ->
            assertTrue(
                surface in helpText,
                "Structural-governance wrapper help must advertise supported surface $surface.",
            )
        }
        setOf(
                "build-logic-kotlin",
                "gradle-kts",
                "markdown-docs",
                "python-support",
                "sqlite-sql",
            )
            .forEach { surface ->
                assertTrue(
                    "--surface $surface" in stageOneText,
                    "Stage 1 must invoke structural governance for $surface.",
                )
            }
        assertTrue(
            "--surface shell-release" !in stageOneText,
            "Stage 1 must leave shell-release to the later release/control-plane stages instead of duplicating that owner here.",
        )
    }

    @Test
    fun javaStructuralReports_publishRepoLocalAuditMirrors() {
        val layoutText = filesystemLayout.readText()
        assertTrue(
            "tmp/structural-governance" in layoutText,
            "Structural-governance evidence must publish a deterministic repo-local audit mirror.",
        )
    }

    @Test
    fun javaDuplicationVerifier_rendersXmlOnlyThroughItsOwnedWriter() {
        val verifierSource = javaDuplicationVerifier.readText()
        assertTrue(
            "XMLRenderer()" in verifierSource,
            "Java duplication verification must own XML rendering explicitly instead of relying on CPD's implicit renderer path.",
        )
        assertTrue(
            "rendererName = \"xml\"" !in verifierSource,
            "Java duplication verification must not configure CPD's implicit XML renderer because that leaks raw XML into the gate output.",
        )
    }

    private fun explicitRuleRefs(document: org.w3c.dom.Document): Set<String> =
        document
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

    private fun excludedRuleNames(document: org.w3c.dom.Document): Set<String> =
        document
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

    private fun ruleProperty(
        document: org.w3c.dom.Document,
        ruleRef: String,
        propertyName: String,
    ): String {
        val rules = document.getElementsByTagName("rule")
        for (index in 0 until rules.length) {
            val rule = rules.item(index)
            val ref = rule.attributes?.getNamedItem("ref")?.nodeValue ?: continue
            if (ref != ruleRef) {
                continue
            }
            val properties = rule.childNodes
            for (propertyIndex in 0 until properties.length) {
                val propertyNode = properties.item(propertyIndex)
                if (propertyNode.nodeName != "properties") {
                    continue
                }
                val propertyNodes = propertyNode.childNodes
                for (childIndex in 0 until propertyNodes.length) {
                    val property = propertyNodes.item(childIndex)
                    if (property.nodeName != "property") {
                        continue
                    }
                    val name = property.attributes?.getNamedItem("name")?.nodeValue ?: continue
                    if (name == propertyName) {
                        return property.attributes?.getNamedItem("value")?.nodeValue
                            ?: error("Missing value for $ruleRef::$propertyName")
                    }
                }
            }
        }
        error("Missing property $ruleRef::$propertyName")
    }

    private fun parseRuleset(path: Path) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.inputStream())
}
