package dev.erst.fingrind.buildlogic

import java.io.Serializable
import java.util.Properties
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.api.provider.Provider

class FinGrindJazzerConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("java")
            pluginManager.apply("dev.erst.fingrind.java-conventions")

            description = "Local-only Jazzer fuzzing layer for FinGrind"

            gradle.startParameter.projectCacheDir?.let { projectCacheDir ->
                layout.buildDirectory.set(file(projectCacheDir.resolve("jazzer-build")))
            }

            configureFinGrindArtifactRepositories()

            val topology = JazzerTopology.load(this)
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            val buildMetadata = FinGrindBuildMetadata.load(this)
            val fingrindJavaVersion = buildMetadata.javaVersion
            val jazzerMaxDuration = providers.gradleProperty("jazzerMaxDuration").orNull
            val jazzerMaxExecutions = providers.gradleProperty("jazzerMaxExecutions").orNull
            registerJavaSourcePolicyTask()
            registerJacksonDependencyPolicyTask()
            val repoRootDirectory = layout.projectDirectory.dir("..")
            val sharedGradleProperties =
                Properties().apply {
                    repoRootDirectory.file("gradle.properties").asFile.inputStream().use(::load)
                }

            fun sharedFingrindProperty(name: String): String =
                sharedGradleProperties.getProperty(name)
                    ?: throw IllegalArgumentException("Missing shared FinGrind property '$name' in ../gradle.properties.")

            val managedSqlite =
                ManagedSqliteProvisioningLogic.register(
                    project = this,
                    repositoryRootDirectory = repoRootDirectory.asFile.toPath(),
                    sourceDirectory =
                        repoRootDirectory.dir(
                            "third_party/sqlite/${sharedFingrindProperty("fingrindManagedSqlitePackageId")}",
                        ),
                    sqliteVersionValue =
                        DistributionContractReader.requiredMinimumSqliteVersion(
                            repoRootDirectory.asFile.toPath(),
                        ),
                    sqlite3mcVersionValue =
                        DistributionContractReader.requiredSqlite3mcVersion(
                            repoRootDirectory.asFile.toPath(),
                        ),
                    sourceSha3 = sharedFingrindProperty("fingrindManagedSqliteSourceSha3"),
                )
            ManagedSqliteProvisioningLogic.configureConsumers(this, managedSqlite)

            extensions.configure<JavaPluginExtension> {
                toolchain.languageVersion.set(JavaLanguageVersion.of(fingrindJavaVersion))
            }

            tasks.withType<JavaCompile>().configureEach {
                options.release.set(fingrindJavaVersion)
            }

            val sourceSets = extensions.getByType<SourceSetContainer>()
            val mainSourceSet = sourceSets.getByName("main")
            val fuzzSourceSet = sourceSets.create("fuzz") {
                java.setSrcDirs(listOf("src/fuzz/java"))
                resources.setSrcDirs(listOf("src/fuzz/resources"))
            }
            val jazzerAgentJar =
                tasks.named<Jar>("jar") {
                    manifest.attributes(
                        mapOf(
                            "Premain-Class" to JAZZER_PREMAIN_CLASS,
                            "Agent-Class" to JAZZER_PREMAIN_CLASS,
                            "Can-Redefine-Classes" to "true",
                            "Can-Retransform-Classes" to "true",
                            "Can-Set-Native-Method-Prefix" to "true",
                        ),
                    )
                }
            fuzzSourceSet.compileClasspath += mainSourceSet.output
            fuzzSourceSet.runtimeClasspath += mainSourceSet.output

            configurations.named(fuzzSourceSet.implementationConfigurationName) {
                extendsFrom(configurations.getByName("implementation"))
            }
            configurations.named(fuzzSourceSet.runtimeOnlyConfigurationName) {
                extendsFrom(configurations.getByName("runtimeOnly"))
            }

            dependencies.apply {
                add("implementation", platform(libs.library("junit-bom")))
                add("implementation", libs.library("junit-platform-launcher"))
                add("implementation", libs.library("jazzer-api"))
                add("implementation", libs.library("jazzer"))
                add("implementation", libs.library("jazzer-junit"))
                add("implementation", libs.library("jackson-databind"))
                add("compileOnly", libs.library("jspecify"))
                add("implementation", "dev.erst.fingrind:contract")
                add("implementation", "dev.erst.fingrind:core")
                add("implementation", "dev.erst.fingrind:executor")
                add("implementation", project.dependencies.testFixtures("dev.erst.fingrind:executor"))
                add("implementation", "dev.erst.fingrind:sqlite")
                add("implementation", "dev.erst.fingrind:cli")

                add("testImplementation", platform(libs.library("junit-bom")))
                add("testImplementation", libs.library("jackson-databind"))
                add("testCompileOnly", libs.library("jspecify"))
                add("testImplementation", libs.library("jazzer-junit"))
                add("testImplementation", libs.library("junit-jupiter"))
                add("testRuntimeOnly", libs.library("junit-platform-launcher"))

                add(fuzzSourceSet.implementationConfigurationName, platform(libs.library("junit-bom")))
                add(fuzzSourceSet.implementationConfigurationName, libs.library("junit-jupiter"))
                add(fuzzSourceSet.compileOnlyConfigurationName, libs.library("jspecify"))
                add(fuzzSourceSet.runtimeOnlyConfigurationName, libs.library("junit-platform-launcher"))
                add(fuzzSourceSet.implementationConfigurationName, libs.library("jazzer-junit"))
                add(fuzzSourceSet.implementationConfigurationName, libs.library("jazzer-api"))
                add(fuzzSourceSet.runtimeOnlyConfigurationName, libs.library("jazzer"))
            }

            tasks.withType<Pmd>().matching { it.name == "pmdFuzz" }.configureEach {
                ruleSetFiles = files(rootProject.file("gradle/pmd/fuzz-ruleset.xml"))
                ruleSets = emptyList()
            }

            fun JavaExec.configureHarnessRuntime() {
                classpath = fuzzSourceSet.runtimeClasspath
                mainClass.set("dev.erst.fingrind.jazzer.tool.JazzerHarnessRunner")
                outputs.upToDateWhen { false }
                workingDir = layout.projectDirectory.asFile
                dependsOn(jazzerAgentJar)
                enableNativeAccess()
                jvmArgs("-javaagent:${jazzerAgentJar.flatMap { it.archiveFile }.get().asFile.absolutePath}")
                if (jazzerMaxDuration != null) {
                    systemProperty("jazzer.max_duration", jazzerMaxDuration)
                }
                if (jazzerMaxExecutions != null) {
                    systemProperty("jazzer.max_executions", jazzerMaxExecutions)
                }
            }

            fun JavaExec.configureMainSourceSet() {
                classpath = mainSourceSet.runtimeClasspath
                outputs.upToDateWhen { false }
                workingDir = layout.projectDirectory.asFile
                enableNativeAccess()
            }

            fun registerToolTask(
                name: String,
                descriptionText: String,
                argumentsProvider: Provider<List<String>>,
            ) = tasks.register<JavaExec>(name) {
                description = descriptionText
                group = "verification"
                configureMainSourceSet()
                mainClass.set("dev.erst.fingrind.jazzer.tool.JazzerCli")
                argumentProviders.add(ProviderBackedArguments(argumentsProvider))
            }

            fun registerFuzzTask(
                target: JazzerRunTargetSpec,
                harness: JazzerHarnessSpec,
            ) = tasks.register<JavaExec>(target.taskName) {
                description = "Actively fuzzes ${harness.displayName.lowercase()}."
                group = "verification"
                configureHarnessRuntime()
                args("--class", harness.className)
                workingDir = layout.projectDirectory.dir(target.workingDirectory).asFile
                doFirst {
                    workingDir.mkdirs()
                }
                environment("JAZZER_FUZZ", "1")
            }

            fun registerRegressionTask(harness: JazzerHarnessSpec) =
                tasks.register<JavaExec>("regression${harness.key.toTaskSuffix()}") {
                    description = "Replays committed ${harness.key} seeds in regression mode."
                    group = "verification"
                    configureMainSourceSet()
                    mainClass.set("dev.erst.fingrind.jazzer.tool.JazzerRegressionRunner")
                    args("--target", harness.key)
                    workingDir = layout.projectDirectory.asFile
                }

            val regressionTasks = topology.harnesses.map(::registerRegressionTask)

            val fuzzTasks =
                topology.runTargets
                    .filter(JazzerRunTargetSpec::activeFuzzing)
                    .map { target ->
                        registerFuzzTask(target = target, harness = topology.harness(target.key))
                    }

            val regressionTarget = topology.runTarget("regression")
            val jazzerRegression =
                tasks.register(regressionTarget.taskName) {
                    description = "Runs all FinGrind Jazzer harnesses in regression mode."
                    group = "verification"
                    dependsOn(regressionTasks)
                }

            val jazzerTargetProperty = providers.gradleProperty("jazzerTarget")
            val jazzerInputProperty = providers.gradleProperty("jazzerInput")
            val jazzerJsonOutputProperty = providers.gradleProperty("jazzerJsonOutput")

            registerToolTask(
                "jazzerReplay",
                "Replays one local input against a single Jazzer harness.",
                providers.provider {
                    buildList {
                        add("replay")
                        add("--target")
                        add(
                            jazzerTargetProperty.orNull
                                ?: throw IllegalArgumentException("Missing Gradle property: jazzerTarget"),
                        )
                        add("--input")
                        add(
                            jazzerInputProperty.orNull
                                ?: throw IllegalArgumentException("Missing Gradle property: jazzerInput"),
                        )
                        if (jazzerJsonOutputProperty.orNull == "true") {
                            add("--json")
                        }
                    }
                },
            )

            registerToolTask(
                "jazzerListFindings",
                "Lists replay-classified local finding artifacts for one or all active Jazzer harnesses.",
                providers.provider {
                    buildList {
                        add("list-findings")
                        jazzerTargetProperty.orNull?.let {
                            add("--target")
                            add(it)
                        }
                        if (jazzerJsonOutputProperty.orNull == "true") {
                            add("--json")
                        }
                    }
                },
            )

            tasks.named<Test>("test") {
                val deterministicTestClassCount =
                    fileTree("src/test/java") {
                        include("**/*Test.java")
                    }.files.size
                description = "Runs deterministic Jazzer replay and harness tests."
                group = "verification"
                useJUnitPlatform()
                maxParallelForks = 1
                enableNativeAccess()
                doFirst {
                    addTestListener(JazzerDeterministicTestPulseListener(deterministicTestClassCount))
                }
            }

            tasks.named("check") {
                dependsOn(jazzerRegression)
            }

            tasks.register("fuzzAllLocal") {
                description = "Runs all active local-only FinGrind fuzzing tasks."
                group = "verification"
                dependsOn(fuzzTasks)
            }

            fuzzTasks.windowed(size = 2, step = 1, partialWindows = false).forEach { (first, second) ->
                second.configure {
                    mustRunAfter(first)
                }
            }

            val jazzerDeterministicTests = tasks.named<Test>("test")
            regressionTasks.windowed(size = 2, step = 1, partialWindows = false).forEach { (first, second) ->
                second.configure {
                    mustRunAfter(first)
                    mustRunAfter(jazzerDeterministicTests)
                }
            }
            regressionTasks.firstOrNull()?.configure {
                mustRunAfter(jazzerDeterministicTests)
            }

            tasks.register<CleanLocalCorpusTask>("cleanLocalCorpus") {
                description = "Deletes generated Jazzer corpora under .local."
                group = "build"
                localDirectory.set(layout.projectDirectory.dir(".local"))
            }

            tasks.register<CleanLocalFindingsTask>("cleanLocalFindings") {
                description = "Deletes local crash files and non-corpus run state under .local."
                group = "build"
                runsDirectory.set(layout.projectDirectory.dir(".local/runs"))
            }
        }
    }
}

private const val JAZZER_PREMAIN_CLASS = "dev.erst.fingrind.jazzer.tool.JazzerPremainAgent"

private class ProviderBackedArguments(
    private val argumentsProvider: Provider<List<String>>,
) : CommandLineArgumentProvider, Serializable {
    override fun asArguments(): Iterable<String> = argumentsProvider.get()
}
