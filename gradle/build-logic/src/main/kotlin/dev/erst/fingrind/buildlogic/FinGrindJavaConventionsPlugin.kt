package dev.erst.fingrind.buildlogic

import java.math.BigDecimal
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

class FinGrindJavaConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("java-base")
            pluginManager.apply("jacoco")

            val fingrindJavaVersion =
                providers.gradleProperty("fingrindJavaVersion").map(String::toInt).get()

            extensions.configure<JavaPluginExtension> {
                toolchain.languageVersion.set(JavaLanguageVersion.of(fingrindJavaVersion))
                withSourcesJar()
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

            tasks.named<JacocoReport>("jacocoTestReport") {
                dependsOn(tasks.withType<Test>())
                reports {
                    xml.required.set(true)
                    html.required.set(true)
                }
            }

            tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
                dependsOn(tasks.withType<Test>())
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
        }
    }
}
