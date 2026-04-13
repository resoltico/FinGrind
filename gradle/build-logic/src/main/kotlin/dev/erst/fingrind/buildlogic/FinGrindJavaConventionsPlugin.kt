package dev.erst.fingrind.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import java.math.BigDecimal
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

class FinGrindJavaConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("java-base")
            pluginManager.apply("jacoco")
            pluginManager.apply("com.diffplug.spotless")
            pluginManager.apply("net.ltgt.errorprone")
            pluginManager.apply("pmd")

            repositories.mavenCentral()

            val libs = versionCatalog()
            val fingrindJavaVersion =
                providers.gradleProperty("fingrindJavaVersion").map(String::toInt).get()

            pluginManager.withPlugin("java") {
                extensions.configure<JavaPluginExtension> {
                    toolchain.languageVersion.set(JavaLanguageVersion.of(fingrindJavaVersion))
                    withSourcesJar()
                }
            }

            dependencies.add("errorprone", libs.library("errorprone-core"))

            extensions.configure<SpotlessExtension> {
                java {
                    target("src/*/java/**/*.java")
                    googleJavaFormat(libs.findVersion("google-java-format").get().requiredVersion)
                    removeUnusedImports()
                    formatAnnotations()
                }
            }

            extensions.configure<PmdExtension> {
                toolVersion = libs.findVersion("pmd").get().requiredVersion
                isConsoleOutput = true
                isIgnoreFailures = false
                rulesMinimumPriority.set(3)
                ruleSetFiles = files(rootProject.file("gradle/pmd/ruleset.xml"))
                ruleSets = emptyList()
            }

            tasks.withType<Jar>().configureEach {
                manifest.attributes(
                    mapOf(
                        "Implementation-Title" to project.name,
                        "Implementation-Version" to project.version,
                        "Implementation-Vendor" to "Ervins Strauhmanis",
                        "Implementation-License" to "MIT",
                        "Enable-Native-Access" to "ALL-UNNAMED",
                    ),
                )
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
                    xml.required.set(true)
                    html.required.set(true)
                }
            }

            tasks.withType<Pmd>().matching { it.name == "pmdTest" }.configureEach {
                ruleSetFiles = files(rootProject.file("gradle/pmd/test-ruleset.xml"))
                ruleSets = emptyList()
            }

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
                enableNativeAccess()

                val progressPulseEnabled =
                    providers.environmentVariable("FINGRIND_TEST_PULSE").map { it == "1" }.orElse(false).get()
                if (progressPulseEnabled) {
                    val progressPulseIntervalMillis =
                        providers.environmentVariable("FINGRIND_TEST_PULSE_INTERVAL_MS")
                            .map(String::toLong)
                            .orElse(15_000L)
                            .get()
                    val pulseTaskPath = path
                    val pulseProjectPath = project.path
                    doFirst {
                        addTestListener(
                            GradleTestPulseListener(
                                logger = logger,
                                taskPath = pulseTaskPath,
                                projectPath = pulseProjectPath,
                                pulseIntervalMillis = progressPulseIntervalMillis,
                            ),
                        )
                    }
                }
            }

            tasks.withType<JavaExec>().configureEach {
                enableNativeAccess()
            }

            extensions.configure<JacocoPluginExtension> {
                toolVersion = libs.findVersion("jacoco").get().requiredVersion
            }

            tasks.named<JacocoReport>("jacocoTestReport") {
                dependsOn(tasks.withType<Test>())
                executionData.from(
                    layout.buildDirectory.dir("jacoco").map { dir ->
                        fileTree(dir) { include("*.exec") }
                    },
                )
                reports {
                    xml.required.set(true)
                    html.required.set(true)
                }
            }

            tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
                dependsOn(tasks.withType<Test>())
                executionData.from(
                    layout.buildDirectory.dir("jacoco").map { dir ->
                        fileTree(dir) { include("*.exec") }
                    },
                )
                violationRules {
                    rule {
                        limit {
                            counter = "LINE"
                            value = "COVEREDRATIO"
                            minimum = BigDecimal("1.0")
                        }
                        limit {
                            counter = "BRANCH"
                            value = "COVEREDRATIO"
                            minimum = BigDecimal("1.0")
                        }
                    }
                }
            }

            tasks.named("check") {
                dependsOn("spotlessCheck")
                dependsOn("jacocoTestCoverageVerification")
            }
        }
    }
}
