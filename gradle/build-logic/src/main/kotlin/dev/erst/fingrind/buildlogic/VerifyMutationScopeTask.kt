package dev.erst.fingrind.buildlogic

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Enforces an explicit PIT disposition for each deterministic accounting-rule source file. */
abstract class VerifyMutationScopeTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:Input
    abstract val targetClasses: SetProperty<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testSourceDirectory: DirectoryProperty

    @get:Input
    abstract val targetTests: SetProperty<String>

    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val excludedProductionClasses: MapProperty<String, String>

    @TaskAction
    fun verifyScope() {
        val productionClasses = sourceClasses(sourceDirectory.get().asFile)
        val configuredTargets = targetClasses.get()
        val exclusions = excludedProductionClasses.get()
        val targetMatches = configuredTargets.associateWith { target ->
            productionClasses.filter { sourceClass -> target.matchesPitPattern(sourceClass) }.toSet()
        }
        val staleTargets = targetMatches.filterValues(Set<String>::isEmpty).keys
        check(staleTargets.isEmpty()) {
            "PIT target classes are stale for ${projectPath.get()}: " + staleTargets.sorted().joinToString() + "."
        }
        val candidates =
            productionClasses.filter { sourceClass ->
                deterministicRuleSourceSuffixes.any(sourceClass::endsWith) ||
                    targetMatches.values.any { sourceClass in it }
            }.toSet()
        val missingDispositions =
            candidates.filter { sourceClass ->
                configuredTargets.none { target -> target.matchesPitPattern(sourceClass) } &&
                    sourceClass !in exclusions
            }
        check(missingDispositions.isEmpty()) {
            "PIT scope admission is missing for ${projectPath.get()}: " +
                missingDispositions.sorted().joinToString() +
                ". Add the class to the mutation scope or record a narrow owning-gate exclusion."
        }

        val staleExclusions = exclusions.keys - productionClasses
        check(staleExclusions.isEmpty()) {
            "PIT scope exclusions are stale for ${projectPath.get()}: " +
                staleExclusions.sorted().joinToString() + "."
        }
        val targetExclusions =
            exclusions.keys.filter { sourceClass ->
                configuredTargets.any { target -> target.matchesPitPattern(sourceClass) }
            }
        check(targetExclusions.isEmpty()) {
            "PIT scope classes cannot be both targeted and excluded for ${projectPath.get()}: " +
                targetExclusions.sorted().joinToString() + "."
        }
        val emptyReasons = exclusions.filterValues(String::isBlank).keys
        check(emptyReasons.isEmpty()) {
            "PIT scope exclusions require an owning-gate reason for ${projectPath.get()}: " +
                emptyReasons.sorted().joinToString() + "."
        }

        val unsupportedTargetTestPatterns = targetTests.get().filter { '*' in it }
        check(unsupportedTargetTestPatterns.isEmpty()) {
            "PIT target tests must name one exact compiled test class for ${projectPath.get()}: " +
                unsupportedTargetTestPatterns.sorted().joinToString() + "."
        }
        val testSources = sourceClasses(testSourceDirectory.get().asFile)
        val staleTargetTests = targetTests.get() - testSources
        check(staleTargetTests.isEmpty()) {
            "PIT target tests are stale for ${projectPath.get()}: " + staleTargetTests.sorted().joinToString() + "."
        }
    }

    private fun sourceClasses(sourceRoot: File): Set<String> =
        sourceRoot
            .walkTopDown()
            .filter(File::isFile)
            .map { source ->
                source
                    .relativeTo(sourceRoot)
                    .path
                    .removeSuffix(".java")
                    .replace(File.separatorChar, '.')
            }.toSet()

    private fun String.matchesPitPattern(sourceClass: String): Boolean {
        val expression =
            buildString {
                append('^')
                this@matchesPitPattern.forEach { character ->
                    if (character == '*') {
                        append(".*")
                    } else {
                        append(Regex.escape(character.toString()))
                    }
                }
                append('$')
            }
        return Regex(expression).matches(sourceClass)
    }

    private companion object {
        val deterministicRuleSourceSuffixes =
            setOf(
                "Accumulator",
                "Calculator",
                "Classifier",
                "Doctrine",
                "Math",
                "Planner",
                "Policy",
                "Resolver",
                "Support",
                "Validator",
            )
    }
}
