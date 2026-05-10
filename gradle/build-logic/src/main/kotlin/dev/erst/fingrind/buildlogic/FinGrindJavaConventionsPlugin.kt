package dev.erst.fingrind.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
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
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

class FinGrindJavaConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            FinGrindFilesystemLayout.projectBuildDirectory(this)?.let { projectBuildDirectory ->
                layout.buildDirectory.set(projectBuildDirectory)
            }

            pluginManager.apply("java-base")
            pluginManager.apply("jacoco")
            pluginManager.apply("com.diffplug.spotless")
            pluginManager.apply("net.ltgt.errorprone")
            pluginManager.apply("pmd")

            configureFinGrindArtifactRepositories()

            val libs = versionCatalog()
            val buildMetadata = FinGrindBuildMetadata.load(this)
            val fingrindJavaVersion = buildMetadata.javaVersion
            val implementationVendor = buildMetadata.implementationVendor
            val implementationLicense = buildMetadata.implementationLicense
            val enforcedErrorProneChecks =
                listOf(
                    "BadImport",
                    "BoxedPrimitiveConstructor",
                    "CheckReturnValue",
                    "EqualsIncompatibleType",
                    "JavaLangClash",
                    "MissingCasesInEnumSwitch",
                    "MissingOverride",
                    "NullAway",
                    "ReferenceEquality",
                    "RequireExplicitNullMarking",
                    "StringCaseLocaleUsage",
                )

            pluginManager.withPlugin("java") {
                extensions.configure<JavaPluginExtension> {
                    toolchain.languageVersion.set(JavaLanguageVersion.of(fingrindJavaVersion))
                    withSourcesJar()
                }
                dependencies.add(
                    "testImplementation",
                    project.dependencies.platform(libs.library("junit-bom")),
                )
                dependencies.add("testImplementation", libs.library("junit-jupiter"))
                dependencies.add("testRuntimeOnly", libs.library("junit-platform-launcher"))
            }

            dependencies.add("errorprone", libs.library("errorprone-core"))
            dependencies.add("errorprone", libs.library("nullaway"))
            dependencies.add("compileOnly", libs.library("jspecify"))
            dependencies.add("testCompileOnly", libs.library("jspecify"))
            pluginManager.withPlugin("java-test-fixtures") {
                dependencies.add("testFixturesCompileOnly", libs.library("jspecify"))
            }
            val sourcePolicyTask = registerJavaSourcePolicyTask()
            val jacksonDependencyPolicyTask = registerJacksonDependencyPolicyTask()

            extensions.configure<SpotlessExtension> {
                lineEndings = LineEnding.UNIX
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
                inputs.property("manifestImplementationTitle", project.name)
                inputs.property("manifestImplementationVersion", project.version.toString())
                inputs.property("manifestImplementationVendor", implementationVendor)
                inputs.property("manifestImplementationLicense", implementationLicense)
                manifest.attributes(
                    mapOf(
                        "Implementation-Title" to project.name,
                        "Implementation-Version" to project.version,
                        "Implementation-Vendor" to implementationVendor,
                        "Implementation-License" to implementationLicense,
                    ),
                )
            }

            tasks.withType<JavaCompile>().configureEach {
                options.release.set(fingrindJavaVersion)
                options.errorprone.disableWarningsInGeneratedCode.set(true)
                options.errorprone.option("NullAway:OnlyNullMarked", "true")
                options.errorprone.option("NullAway:JSpecifyMode", "true")
                options.errorprone.error(*enforcedErrorProneChecks.toTypedArray())
            }

            val compileJava = tasks.named<JavaCompile>("compileJava")
            val pruneCompileJavaOutputs =
                tasks.register("pruneMainCompileJavaOutputs") {
                    val destinationDirectory = compileJava.flatMap { it.destinationDirectory }
                    outputs.dir(destinationDirectory)
                    outputs.upToDateWhen { false }
                    doLast {
                        val outputDirectory = destinationDirectory.get().asFile
                        outputDirectory.deleteRecursively()
                        outputDirectory.mkdirs()
                    }
                }

            compileJava.configure {
                options.isIncremental = false
                outputs.upToDateWhen { false }
                dependsOn(pruneCompileJavaOutputs)
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
                val jacocoDestinationFile =
                    FinGrindFilesystemLayout.jacocoDestinationFile(project, name)
                extensions.configure(JacocoTaskExtension::class.java) {
                    destinationFile = jacocoDestinationFile
                }
                doFirst {
                    jacocoDestinationFile.parentFile.mkdirs()
                    if (jacocoDestinationFile.exists() && !jacocoDestinationFile.delete()) {
                        throw IllegalStateException(
                            "Unable to reset stale JaCoCo execution data at ${jacocoDestinationFile.absolutePath}.",
                        )
                    }
                }

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
            val testTasks = tasks.withType<Test>()
            val jacocoExecutionData =
                providers.provider<List<java.io.File>> {
                    testTasks.mapNotNull { testTask ->
                        testTask.extensions.findByType(JacocoTaskExtension::class.java)?.destinationFile
                    }
                }

            tasks.withType<JavaExec>().configureEach {
                enableNativeAccess()
            }

            extensions.configure<JacocoPluginExtension> {
                toolVersion = libs.findVersion("jacoco").get().requiredVersion
            }

            val mainSourceSet =
                extensions.findByType(JavaPluginExtension::class.java)?.sourceSets?.findByName("main")
                    ?: throw IllegalStateException(
                        "FinGrind Java conventions require a Java main source set for JaCoCo configuration.",
                    )
            val jacocoXmlReport = layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml")
            tasks.named<JacocoReport>("jacocoTestReport") {
                dependsOn(testTasks)
                executionData.from(jacocoExecutionData)
                classDirectories.setFrom(mainSourceSet.output.classesDirs)
                sourceDirectories.setFrom(mainSourceSet.allJava.srcDirs)
                reports {
                    xml.required.set(true)
                    xml.outputLocation.set(jacocoXmlReport)
                    html.required.set(true)
                }
            }

            tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
                dependsOn("jacocoTestReport")
                executionData.from(jacocoExecutionData)
                classDirectories.setFrom(mainSourceSet.output.classesDirs)
                sourceDirectories.setFrom(mainSourceSet.allJava.srcDirs)
                doLast {
                    JacocoXmlCoverageVerifier.verifyReport(jacocoXmlReport.get().asFile)
                }
            }

            tasks.named("check") {
                dependsOn("spotlessCheck")
                dependsOn("jacocoTestCoverageVerification")
                dependsOn(sourcePolicyTask)
                dependsOn(jacksonDependencyPolicyTask)
            }
        }
    }
}
