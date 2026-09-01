package dev.erst.fingrind.buildlogic

import info.solidsoft.gradle.pitest.PitestPlugin
import info.solidsoft.gradle.pitest.PitestPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Delete
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/** Shared PIT wiring for release-critical deterministic accounting scopes. */
class FinGrindMutationConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(PitestPlugin::class.java)
        val scope = project.extensions.create<FinGrindMutationScopeExtension>("fingrindMutation")
        val versions = project.extensions.getByType<VersionCatalogsExtension>().named("libs")
        val toolchains = project.extensions.getByType<JavaToolchainService>()
        val javaVersion = FinGrindBuildMetadata.load(project).javaVersion
        val pitReportDirectory = project.layout.buildDirectory.dir("reports/pitest")
        val cleanReport =
            project.tasks.register<Delete>("cleanPitestReport") {
                delete(pitReportDirectory)
            }

        project.extensions.configure<PitestPluginExtension> {
            pitestVersion.set(versions.findVersion("pitest").get().requiredVersion)
            junit5PluginVersion.set(
                versions.findVersion("pitest-junit5-plugin").get().requiredVersion,
            )
            targetClasses.set(scope.targetClasses)
            targetTests.set(scope.targetTests)
            mutators.set(setOf("DEFAULTS", "EXPERIMENTAL_SWITCH"))
            mutationThreshold.set(100)
            coverageThreshold.set(95)
            testStrengthThreshold.set(100)
            maxSurviving.set(0)
            threads.set(Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
            outputFormats.set(setOf("XML", "HTML"))
            reportDir.set(pitReportDirectory)
            timestampedReports.set(false)
            failWhenNoMutations.set(true)
            jvmArgs.set(listOf(UNNAMED_NATIVE_ACCESS_ARGUMENT))
            jvmPath.set(
                toolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(javaVersion))
                }.map { launcher -> launcher.executablePath },
            )
        }

        project.tasks.named(PitestPlugin.PITEST_TASK_NAME).configure {
            dependsOn(cleanReport)
            outputs.cacheIf { false }
        }

        val verifyEvidence =
            project.tasks.register<VerifyMutationEvidenceTask>("verifyMutationEvidence") {
                group = "verification"
                description = "Verifies completed PIT evidence for the reviewed mutation scope."
                reportDirectory.set(pitReportDirectory)
                projectPath.set(project.path)
                expectedMutationCounts.putAll(scope.expectedMutationCounts)
                dependsOn(project.tasks.named(PitestPlugin.PITEST_TASK_NAME))
            }
        val verifyScope =
            project.tasks.register<VerifyMutationScopeTask>("verifyMutationScope") {
                group = "verification"
                description = "Verifies every deterministic accounting rule has a PIT disposition."
                sourceDirectory.set(project.layout.projectDirectory.dir("src/main/java"))
                testSourceDirectory.set(project.layout.projectDirectory.dir("src/test/java"))
                projectPath.set(project.path)
                targetClasses.set(scope.targetClasses)
                targetTests.set(scope.targetTests)
                excludedProductionClasses.set(scope.excludedProductionClasses)
            }

        val aggregate = project.rootProject.tasks.maybeCreate("mutationCheck")
        aggregate.group = "verification"
        aggregate.description = "Runs and verifies release-critical mutation-testing scopes."
        aggregate.dependsOn(verifyEvidence)
        aggregate.dependsOn(verifyScope)
    }
}
