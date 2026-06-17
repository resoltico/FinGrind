package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue

class StructuralGovernanceContractTest {
    private val repositoryRoot = Path.of("").toAbsolutePath().normalize().parent.parent
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
    private val javaShapeVerifier =
        repositoryRoot.resolve(
            "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/JavaSourceShapeVerification.kt",
        )

    @Test
    fun checkedInPmdRulesets_matchCanonicalRenderings() {
        FinGrindPmdRulesets.surfaces().forEach { surface ->
            val checkedInRuleset = repositoryRoot.resolve(surface.repositoryRelativePath).readText()
            assertEquals(
                FinGrindPmdRulesets.render(surface),
                checkedInRuleset,
                "Checked-in PMD ruleset ${surface.repositoryRelativePath} drifted from the canonical PMD policy.",
            )
        }
    }

    @Test
    fun canonicalPmdRulesets_keepIntendedStructuralVariantsExplicit() {
        val production = FinGrindPmdRulesets.render(FinGrindPmdRulesetSurface.MAIN_PRODUCTION)
        val mainTest = FinGrindPmdRulesets.render(FinGrindPmdRulesetSurface.MAIN_TEST)
        val jazzerProduction = FinGrindPmdRulesets.render(FinGrindPmdRulesetSurface.JAZZER_PRODUCTION)
        val jazzerTest = FinGrindPmdRulesets.render(FinGrindPmdRulesetSurface.JAZZER_TEST)
        val fuzz = FinGrindPmdRulesets.render(FinGrindPmdRulesetSurface.JAZZER_FUZZ)

        assertEquals(
            production,
            jazzerProduction,
            "Production Java and Jazzer production code must inherit the same canonical PMD policy.",
        )
        assertTrue(
            "<exclude name=\"NcssCount\"/>" in production,
            "Production PMD policy must explicitly exclude NcssCount so file-size ownership stays with structural governance.",
        )
        assertTrue(
            "value=\"16\"" in production,
            "Production PMD policy must pin TooManyMethods explicitly.",
        )
        assertTrue(
            "<exclude name=\"TooManyMethods\"/>" in mainTest,
            "Main test PMD policy must relax TooManyMethods explicitly for test suites.",
        )
        assertTrue(
            """<rule ref="category/java/design.xml/GodClass"/>""" in mainTest,
            "Main test PMD policy must re-add GodClass explicitly.",
        )
        assertTrue(
            "value=\"72\"" in mainTest,
            "Main test PMD policy must keep CouplingBetweenObjects explicitly pinned.",
        )
        assertEquals(
            1,
            """<rule ref="category/java/design.xml/GodClass"/>""".toRegex().findAll(jazzerTest).count(),
            "Jazzer test PMD policy must keep GodClass enforced exactly once.",
        )
        assertTrue(
            "<exclude name=\"NcssCount\"/>" in jazzerTest,
            "Jazzer test PMD policy must explicitly exclude NcssCount so file-size ownership stays with structural governance.",
        )
        assertTrue(
            "value=\"72\"" in jazzerTest,
            "Jazzer test PMD policy must keep CouplingBetweenObjects explicitly pinned instead of silently dropping the rule.",
        )
        assertTrue(
            "<exclude name=\"TestClassWithoutTestCases\"/>" in fuzz,
            "Fuzz PMD policy must document the @FuzzTest entrypoint relaxation explicitly.",
        )
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
                "json-resource",
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
                "json-resource",
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

    @Test
    fun javaVerificationTasks_captureModuleIdentityAsDeclaredInputs() {
        listOf(javaDuplicationVerifier, javaShapeVerifier).forEach { verifier ->
            val verifierSource = verifier.readText()
            assertTrue(
                "abstract val moduleName: Property<String>" in verifierSource,
                "Java verification task ${verifier.fileName} must declare module identity as an input for configuration-cache-safe execution.",
            )
            assertTrue(
                "project.name" !in verifierSource,
                "Java verification task ${verifier.fileName} must not read project.name during task execution.",
            )
            assertTrue(
                "abstract val reviewedSurfaceRegistryFiles: ConfigurableFileCollection" in verifierSource,
                "Java verification task ${verifier.fileName} must declare reviewed-surface registry fragments as explicit inputs so waiver edits invalidate the task graph.",
            )
            assertTrue(
                "ReviewedSurfaceRegistry.registryFragmentFiles(projectDir.toPath())" in verifierSource,
                "Java verification task ${verifier.fileName} must source its registry-file inputs from the reviewed-surface registry locator instead of relying on daemon memory.",
            )
        }
    }
}
