import com.diffplug.gradle.spotless.SpotlessExtension
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    base
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.shadow) apply false
}

description = providers.gradleProperty("fingrindDescription").get()

// Root-level JaCoCo configuration for the aggregated report task.
repositories {
    mavenCentral()
}

configure<JacocoPluginExtension> {
    toolVersion = libs.versions.jacoco.get()
}

allprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()
}

subprojects {
    repositories {
        mavenCentral()
    }

    pluginManager.withPlugin("java-base") {
        apply(plugin = "com.diffplug.spotless")
        apply(plugin = "net.ltgt.errorprone")
        apply(plugin = "pmd")

        dependencies {
            add("errorprone", libs.errorprone.core.get())
        }

        configure<SpotlessExtension> {
            java {
                target("src/*/java/**/*.java")
                googleJavaFormat(libs.versions.google.java.format.get())
                removeUnusedImports()
                formatAnnotations()
            }
        }

        configure<PmdExtension> {
            toolVersion = libs.versions.pmd.get()
            isConsoleOutput = true
            isIgnoreFailures = false
            rulesMinimumPriority = 3
            ruleSetFiles = files(rootProject.file("gradle/pmd/ruleset.xml"))
            ruleSets = emptyList()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.errorprone.disableWarningsInGeneratedCode.set(true)
            options.errorprone.error(
                "BadImport",
                "BoxedPrimitiveConstructor",
                "CheckReturnValue",
                "EqualsIncompatibleType",
                "JavaLangClash",
                "MissingCasesInEnumSwitch",
                "MissingOverride",
                "ReferenceEquality",
                "StringCaseLocaleUsage"
            )
        }

        tasks.withType<Pmd>().configureEach {
            reports {
                xml.required = true
                html.required = true
            }
        }

        tasks.withType<Pmd>().matching { it.name == "pmdTest" }.configureEach {
            ruleSetFiles = files(rootProject.file("gradle/pmd/test-ruleset.xml"))
            ruleSets = emptyList()
        }

        tasks.named("check") {
            dependsOn("spotlessCheck")
        }
    }

    pluginManager.withPlugin("jacoco") {
        configure<JacocoPluginExtension> {
            toolVersion = libs.versions.jacoco.get()
        }

        tasks.named("check") {
            dependsOn("jacocoTestCoverageVerification")
        }
    }
}

spotless {
    format("projectFiles") {
        target(
            ".gitattributes",
            ".gitignore",
            ".dockerignore",
            "Dockerfile",
            "**/*.gradle.kts",
            "**/*.md",
            "**/*.yml",
            "gradle.properties",
            "gradle/**/*.toml",
            "docs/**/*.json",
            "examples/**/*.json"
        )
        // Exclude all generated build outputs. Gradle and the Kotlin DSL plugin extract
        // and copy source files (e.g. convention plugins) into build/ directories during
        // compilation — those copies must never be checked or reformatted by Spotless.
        targetExclude("**/build/**", "**/.gradle/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}

// ---------------------------------------------------------------------------
// Aggregated JaCoCo report — merges execution data from all Java modules
// ---------------------------------------------------------------------------
val coverageProjects = subprojects.filter { it.name != "jazzer" }

tasks.register<JacocoReport>("jacocoAggregatedReport") {
    group = "verification"
    description = "Aggregates JaCoCo coverage reports from all modules into a single report."

    dependsOn(coverageProjects.map { "${it.path}:test" })

    executionData.from(
        coverageProjects.map { file("${it.projectDir}/build/jacoco/test.exec") }
    )
    sourceDirectories.from(
        coverageProjects.map { file("${it.projectDir}/src/main/java") }
    )
    classDirectories.from(
        coverageProjects.map {
            fileTree("${it.projectDir}/build/classes/java/main") {
                exclude("**/module-info.class")
            }
        }
    )

    reports {
        xml.required = true
        xml.outputLocation = layout.buildDirectory.file("reports/jacoco/aggregated/report.xml")
        html.required = true
        html.outputLocation = layout.buildDirectory.dir("reports/jacoco/aggregated/html")
    }
}

tasks.register("coverage") {
    group = "verification"
    description = "Runs tests, enforces coverage thresholds, and generates per-module and aggregated coverage reports."
    dependsOn(coverageProjects.map { "${it.path}:jacocoTestCoverageVerification" })
    dependsOn(coverageProjects.map { "${it.path}:jacocoTestReport" })
    dependsOn("jacocoAggregatedReport")
}
