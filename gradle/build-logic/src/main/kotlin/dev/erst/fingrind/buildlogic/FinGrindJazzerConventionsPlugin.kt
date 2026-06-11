package dev.erst.fingrind.buildlogic

import java.io.Serializable
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.api.provider.Provider

internal const val jazzerTestProjectRootProperty = "fingrind.jazzer.test-project-root"
private const val jazzerWrapperExitStatusProperty = "fingrind.jazzer.wrapper.exit-status-file"

class FinGrindJazzerConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("java")
            pluginManager.apply("dev.erst.fingrind.java-conventions")

            gradle.startParameter.projectCacheDir?.let { projectCacheDir ->
                layout.buildDirectory.set(file(projectCacheDir.resolve("jazzer-build")))
            }

            description = "Local-only Jazzer fuzzing layer for FinGrind"

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
            val repositoryRootPath = repoRootDirectory.asFile.toPath()
            val managedSqlitePackageId =
                DistributionContractReader.requiredSqliteSourcePackageId(repositoryRootPath)

            val managedSqlite =
                ManagedSqliteProvisioningLogic.register(
                    project = this,
                    repositoryRootDirectory = repositoryRootPath,
                    sqliteSourceDirectory =
                        repoRootDirectory.dir(
                            "third_party/sqlite/$managedSqlitePackageId",
                        ),
                    sqliteVersionValue =
                        DistributionContractReader.requiredMinimumSqliteVersion(
                            repositoryRootPath,
                        ),
                    sqlite3mcVersionValue =
                        DistributionContractReader.requiredSqlite3mcVersion(
                            repositoryRootPath,
                        ),
                    sourcePackageId = managedSqlitePackageId,
                )
            ManagedSqliteProvisioningLogic.configureConsumers(this, managedSqlite)

            extensions.configure<JavaPluginExtension> {
                toolchain.languageVersion.set(JavaLanguageVersion.of(fingrindJavaVersion))
            }

            tasks.withType<JavaCompile>().configureEach {
                options.release.set(fingrindJavaVersion)
            }

            tasks.withType<ProcessResources>().configureEach {
                outputs.upToDateWhen { false }
                doFirst {
                    // The nested Jazzer build also mirrors committed regression seeds and metadata
                    // through Gradle resource outputs. Copy-style resource processing does not
                    // prune renamed or removed inputs on its own, so clear the destination
                    // directory before each real resource sync to keep packaged corpora aligned
                    // with src/fuzz/resources instead of retaining stale historical seeds.
                    destinationDir.deleteRecursively()
                }
            }

            val sourceSets = extensions.getByType<SourceSetContainer>()
            val mainSourceSet = sourceSets.getByName("main")
            val testSourceSet = sourceSets.getByName("test")
            val fuzzSourceSet = sourceSets.create("fuzz") {
                java.setSrcDirs(listOf("src/fuzz/java"))
                resources.setSrcDirs(listOf("src/fuzz/resources"))
            }
            fun registerWhiteBoxModulePatch(
                taskName: String,
                directoryName: String,
                packagePath: String,
                fixtureJarPattern: Regex,
                localOutputDirectories: List<Any> = emptyList(),
            ): Pair<org.gradle.api.provider.Provider<org.gradle.api.file.Directory>, org.gradle.api.tasks.TaskProvider<Sync>> {
                val patchDirectory = layout.buildDirectory.dir(directoryName)
                val fixtureArtifacts =
                    providers.provider {
                        testSourceSet.runtimeClasspath.files.filter { runtimeEntry ->
                            runtimeEntry.isFile && runtimeEntry.name.matches(fixtureJarPattern)
                        }
                    }
                val patchTask =
                    tasks.register<Sync>(taskName) {
                        localOutputDirectories.forEach { outputDirectory ->
                            from(outputDirectory) {
                                include("$packagePath/**")
                            }
                        }
                        from(
                            fixtureArtifacts.map { runtimeArtifacts ->
                                runtimeArtifacts.map(::zipTree)
                            },
                        ) {
                            include("$packagePath/**")
                        }
                        into(patchDirectory)
                    }
                return patchDirectory to patchTask
            }

            val (sqliteWhiteBoxTestPatchDirectory, sqliteWhiteBoxTestPatch) =
                registerWhiteBoxModulePatch(
                    taskName = "stageSqliteWhiteBoxTestPatch",
                    directoryName = "sqlite-white-box-patch",
                    packagePath = "dev/erst/fingrind/sqlite",
                    fixtureJarPattern = Regex("""sqlite-.*-test-fixtures\.jar"""),
                    localOutputDirectories =
                        listOf(mainSourceSet.output.classesDirs, testSourceSet.output.classesDirs),
                )
            val (executorWhiteBoxTestPatchDirectory, executorWhiteBoxTestPatch) =
                registerWhiteBoxModulePatch(
                    taskName = "stageExecutorWhiteBoxTestPatch",
                    directoryName = "executor-white-box-patch",
                    packagePath = "dev/erst/fingrind/executor",
                    fixtureJarPattern = Regex("""executor-.*-test-fixtures\.jar"""),
                )
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
            testSourceSet.compileClasspath += fuzzSourceSet.output
            testSourceSet.runtimeClasspath += fuzzSourceSet.output

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
                add("implementation", project.dependencies.testFixtures("dev.erst.fingrind:sqlite"))
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
                dependsOn(executorWhiteBoxTestPatch)
                dependsOn(sqliteWhiteBoxTestPatch)
                enableUnnamedNativeAccess()
                allowSunMiscUnsafeMemoryAccess()
                disableClassDataSharing()
                patchModule(
                    "dev.erst.fingrind.sqlite",
                    files(sqliteWhiteBoxTestPatchDirectory),
                )
                patchModule(
                    "dev.erst.fingrind.executor",
                    files(executorWhiteBoxTestPatchDirectory),
                )
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
                dependsOn(executorWhiteBoxTestPatch)
                dependsOn(sqliteWhiteBoxTestPatch)
                enableUnnamedNativeAccess()
                allowSunMiscUnsafeMemoryAccess()
                disableClassDataSharing()
                patchModule(
                    "dev.erst.fingrind.sqlite",
                    files(sqliteWhiteBoxTestPatchDirectory),
                )
                patchModule(
                    "dev.erst.fingrind.executor",
                    files(executorWhiteBoxTestPatchDirectory),
                )
            }

            fun registerToolTask(
                name: String,
                descriptionText: String,
                argumentsProvider: Provider<List<String>>,
                requiresProjectRoot: Boolean = true,
                wrapperExitStatusFile: Provider<String> =
                    providers.gradleProperty("__unusedJazzerWrapperExitStatusFile"),
            ) = tasks.register<JavaExec>(name) {
                description = descriptionText
                group = "verification"
                configureMainSourceSet()
                mainClass.set("dev.erst.fingrind.jazzer.tool.JazzerCli")
                wrapperExitStatusFile.orNull?.let { exitStatusFile ->
                    isIgnoreExitValue = true
                    systemProperty(jazzerWrapperExitStatusProperty, exitStatusFile)
                }
                argumentProviders.add(
                    ProviderBackedArguments(
                        providers.provider {
                            if (!requiresProjectRoot) {
                                argumentsProvider.get()
                            } else {
                                listOf("--project-root", layout.projectDirectory.asFile.absolutePath) +
                                    argumentsProvider.get()
                            }
                        },
                    ),
                )
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
                    args("--project-root", layout.projectDirectory.asFile.absolutePath, "--target", harness.key)
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
            val jazzerSeedNameProperty = providers.gradleProperty("jazzerSeedName")
            val jazzerSeedIntentProperty = providers.gradleProperty("jazzerSeedIntent")
            val jazzerJsonOutputProperty = providers.gradleProperty("jazzerJsonOutput")
            val jazzerWrapperExitStatusFileProperty =
                providers.gradleProperty("fingrindJazzerWrapperExitStatusFile")

            registerToolTask(
                "jazzerReplay",
                "Replays one local input against a single Jazzer harness.",
                providers.provider {
                    buildList {
                        add("replay")
                        add(
                            jazzerTargetProperty.orNull
                                ?: throw IllegalArgumentException("Missing Gradle property: jazzerTarget"),
                        )
                        add(
                            jazzerInputProperty.orNull
                                ?: throw IllegalArgumentException("Missing Gradle property: jazzerInput"),
                        )
                        if (jazzerJsonOutputProperty.orNull == "true") {
                            add("--json")
                        }
                    }
                },
                wrapperExitStatusFile = jazzerWrapperExitStatusFileProperty,
            )

            registerToolTask(
                "jazzerListFindings",
                "Lists replay-classified local finding artifacts for one or all active Jazzer harnesses.",
                providers.provider {
                    buildList {
                        add("list-findings")
                        jazzerTargetProperty.orNull?.let(::add)
                        if (jazzerJsonOutputProperty.orNull == "true") {
                            add("--json")
                        }
                    }
                },
                wrapperExitStatusFile = jazzerWrapperExitStatusFileProperty,
            )

            registerToolTask(
                "jazzerPromoteSeed",
                "Promotes one ad hoc replay input into the committed Jazzer seed floor.",
                providers.provider {
                    buildList {
                        add("promote-seed")
                        add(
                            jazzerTargetProperty.orNull
                                ?: throw IllegalArgumentException("Missing Gradle property: jazzerTarget"),
                        )
                        add(
                            jazzerInputProperty.orNull
                                ?: throw IllegalArgumentException("Missing Gradle property: jazzerInput"),
                        )
                        add("--name")
                        add(
                            jazzerSeedNameProperty.orNull
                                ?: throw IllegalArgumentException("Missing Gradle property: jazzerSeedName"),
                        )
                        add("--intent")
                        add(
                            jazzerSeedIntentProperty.orNull
                                ?: throw IllegalArgumentException("Missing Gradle property: jazzerSeedIntent"),
                        )
                        if (jazzerJsonOutputProperty.orNull == "true") {
                            add("--json")
                        }
                    }
                },
                wrapperExitStatusFile = jazzerWrapperExitStatusFileProperty,
            )

            registerToolTask(
                "jazzerSeedAudit",
                "Summarizes committed Jazzer seeds and reports duplicate-content defects.",
                providers.provider {
                    buildList {
                        add("seed-audit")
                        jazzerTargetProperty.orNull?.let(::add)
                        if (jazzerJsonOutputProperty.orNull == "true") {
                            add("--json")
                        }
                    }
                },
                wrapperExitStatusFile = jazzerWrapperExitStatusFileProperty,
            )

            registerToolTask(
                "jazzerActiveTargets",
                "Prints the active local-only Jazzer target keys in canonical topology order.",
                providers.provider { listOf("active-target-keys") },
                requiresProjectRoot = false,
            )

            tasks.register("jazzerReplayableTargets") {
                description = "Prints replayable Jazzer target keys in canonical topology order."
                group = "verification"
                doLast {
                    topology.runTargets
                        .filter { it.harnessKeys.size == 1 }
                        .forEach { println(it.key) }
                }
            }

            configureJazzerVerificationLifecycle(
                fuzzTasks = fuzzTasks,
                regressionTasks = regressionTasks,
                jazzerRegression = jazzerRegression,
                sqliteWhiteBoxTestPatch = sqliteWhiteBoxTestPatch,
                executorWhiteBoxTestPatch = executorWhiteBoxTestPatch,
                sqliteWhiteBoxTestPatchDirectory = sqliteWhiteBoxTestPatchDirectory,
                executorWhiteBoxTestPatchDirectory = executorWhiteBoxTestPatchDirectory,
            )
        }
    }
}

private const val JAZZER_PREMAIN_CLASS = "dev.erst.fingrind.jazzer.tool.JazzerPremainAgent"

private class ProviderBackedArguments(
    private val argumentsProvider: Provider<List<String>>,
) : CommandLineArgumentProvider, Serializable {
    override fun asArguments(): Iterable<String> = argumentsProvider.get()
}
