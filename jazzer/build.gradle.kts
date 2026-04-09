import dev.erst.fingrind.jazzer.build.JazzerSupportTestPulseListener
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.util.Comparator
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

plugins {
    java
}

description = "Local-only Jazzer fuzzing layer for FinGrind"

repositories {
    mavenCentral()
}

abstract class CleanLocalCorpusTask : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    abstract val localDirectory: DirectoryProperty

    @TaskAction
    fun clean() {
        val localPath = localDirectory.asFile.orNull?.toPath() ?: return
        if (!Files.exists(localPath)) {
            return
        }
        Files.walk(localPath).use { localStream ->
            localStream
                .filter { path -> path.fileName.toString() == ".cifuzz-corpus" }
                .sorted(Comparator.reverseOrder())
                .forEach { corpusPath ->
                    Files.walk(corpusPath).use { corpusStream ->
                        corpusStream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                    }
                }
        }
    }
}

abstract class CleanLocalFindingsTask : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    abstract val runsDirectory: DirectoryProperty

    @TaskAction
    fun clean() {
        val runsPath = runsDirectory.asFile.orNull?.toPath() ?: return
        if (!Files.exists(runsPath)) {
            return
        }
        Files.walk(runsPath).use { runsStream ->
            runsStream.sorted(Comparator.reverseOrder()).forEach { path ->
                if (path == runsPath) {
                    return@forEach
                }
                if (path.fileName.toString() == ".cifuzz-corpus") {
                    return@forEach
                }
                if (path.iterator().asSequence().any { it.toString() == ".cifuzz-corpus" }) {
                    return@forEach
                }
                try {
                    Files.deleteIfExists(path)
                } catch (_: DirectoryNotEmptyException) {
                    // Preserved corpus content intentionally keeps some run directories alive.
                }
            }
        }
    }
}

val fingrindJavaVersion: Int =
    providers.gradleProperty("fingrindJavaVersion").map(String::toInt).get()
val fingrindSqliteWrapper: String = file("../scripts/sqlite3.sh").absolutePath
val jazzerMaxDuration = providers.gradleProperty("jazzerMaxDuration").orNull
val jazzerMaxExecutions = providers.gradleProperty("jazzerMaxExecutions").orNull

data class HarnessDefinition(
    val key: String,
    val className: String
)

val cliRequestHarness =
    HarnessDefinition(
        "cli-request",
        "dev.erst.fingrind.cli.CliRequestFuzzTest"
    )
val postingWorkflowHarness =
    HarnessDefinition(
        "posting-workflow",
        "dev.erst.fingrind.cli.PostingWorkflowFuzzTest"
    )
val sqliteBookRoundTripHarness =
    HarnessDefinition(
        "sqlite-book-roundtrip",
        "dev.erst.fingrind.cli.SqliteBookRoundTripFuzzTest"
    )

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion = JavaLanguageVersion.of(fingrindJavaVersion)
    }
}

val sourceSets = extensions.getByType(SourceSetContainer::class)
val mainSourceSet = sourceSets["main"]
val fuzzSourceSet = sourceSets.create("fuzz") {
    java.setSrcDirs(listOf("src/fuzz/java"))
    resources.setSrcDirs(listOf("src/fuzz/resources"))
}
fuzzSourceSet.compileClasspath += mainSourceSet.output
fuzzSourceSet.runtimeClasspath += mainSourceSet.output

configurations.named(fuzzSourceSet.implementationConfigurationName) {
    extendsFrom(configurations["implementation"])
}
configurations.named(fuzzSourceSet.runtimeOnlyConfigurationName) {
    extendsFrom(configurations["runtimeOnly"])
}

dependencies {
    add("implementation", platform("org.junit:junit-bom:6.0.3"))
    add("implementation", "org.junit.platform:junit-platform-launcher")
    add("implementation", "tools.jackson.core:jackson-databind:3.1.1")
    add("implementation", "dev.erst.fingrind:core")
    add("implementation", "dev.erst.fingrind:runtime")
    add("implementation", "dev.erst.fingrind:application")
    add("implementation", "dev.erst.fingrind:sqlite")
    add("implementation", "dev.erst.fingrind:cli")
    add("testImplementation", platform("org.junit:junit-bom:6.0.3"))
    add("testImplementation", "tools.jackson.core:jackson-databind:3.1.1")
    add("testImplementation", "org.junit.jupiter:junit-jupiter")
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    add(fuzzSourceSet.implementationConfigurationName, platform("org.junit:junit-bom:6.0.3"))
    add(fuzzSourceSet.implementationConfigurationName, "org.junit.jupiter:junit-jupiter")
    add(fuzzSourceSet.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher")
    add(fuzzSourceSet.implementationConfigurationName, "com.code-intelligence:jazzer-junit:0.30.0")
    add(fuzzSourceSet.implementationConfigurationName, "com.code-intelligence:jazzer-api:0.30.0")
    add(fuzzSourceSet.runtimeOnlyConfigurationName, "com.code-intelligence:jazzer:0.30.0")
}

fun runDirectory(path: String) = layout.projectDirectory.dir(path).asFile

fun JavaExec.configureHarnessRuntime() {
    classpath = fuzzSourceSet.runtimeClasspath
    mainClass.set("dev.erst.fingrind.jazzer.tool.JazzerHarnessRunner")
    outputs.upToDateWhen { false }
    workingDir = layout.projectDirectory.asFile
    environment("FINGRIND_SQLITE3_BINARY", fingrindSqliteWrapper)
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Djdk.attach.allowAttachSelf=true")
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
    environment("FINGRIND_SQLITE3_BINARY", fingrindSqliteWrapper)
}

fun registerHarnessTask(
    name: String,
    descriptionText: String,
    harness: HarnessDefinition,
    workingDirectory: String,
    fuzzing: Boolean
) = tasks.register<JavaExec>(name) {
    description = descriptionText
    group = "verification"
    configureHarnessRuntime()
    args("--class", harness.className)
    workingDir = runDirectory(workingDirectory)
    doFirst {
        workingDir.mkdirs()
    }
    if (fuzzing) {
        environment("JAZZER_FUZZ", "1")
    }
}

fun registerRegressionTask(
    name: String,
    descriptionText: String,
    harness: HarnessDefinition
) = tasks.register<JavaExec>(name) {
    description = descriptionText
    group = "verification"
    configureMainSourceSet()
    mainClass.set("dev.erst.fingrind.jazzer.tool.JazzerRegressionRunner")
    args("--target", harness.key)
}

val regressionCliRequest =
    registerRegressionTask(
        "regressionCliRequest",
        "Replays committed CLI request seeds in regression mode.",
        cliRequestHarness
    )

val regressionPostingWorkflow =
    registerRegressionTask(
        "regressionPostingWorkflow",
        "Replays committed posting workflow seeds in regression mode.",
        postingWorkflowHarness
    )

val regressionSqliteBookRoundTrip =
    registerRegressionTask(
        "regressionSqliteBookRoundTrip",
        "Replays committed SQLite single-book seeds in regression mode.",
        sqliteBookRoundTripHarness
    )

val jazzerRegression =
    tasks.register("jazzerRegression") {
        description = "Runs all FinGrind Jazzer harnesses in regression mode."
        group = "verification"
        dependsOn(
            regressionCliRequest,
            regressionPostingWorkflow,
            regressionSqliteBookRoundTrip
        )
    }

val fuzzCliRequest =
    registerHarnessTask(
        "fuzzCliRequest",
        "Actively fuzzes FinGrind CLI request parsing.",
        cliRequestHarness,
        ".local/runs/cli-request",
        true
    )

val fuzzPostingWorkflow =
    registerHarnessTask(
        "fuzzPostingWorkflow",
        "Actively fuzzes FinGrind posting workflow invariants.",
        postingWorkflowHarness,
        ".local/runs/posting-workflow",
        true
    )

val fuzzSqliteBookRoundTrip =
    registerHarnessTask(
        "fuzzSqliteBookRoundTrip",
        "Actively fuzzes single-book SQLite round-trips at arbitrary filesystem paths.",
        sqliteBookRoundTripHarness,
        ".local/runs/sqlite-book-roundtrip",
        true
    )

tasks.named<Test>("test") {
    val supportTestClassCount =
        fileTree("src/test/java") {
            include("**/*Test.java")
        }.files.size
    description = "Runs deterministic Jazzer support tests."
    group = "verification"
    useJUnitPlatform()
    environment("FINGRIND_SQLITE3_BINARY", fingrindSqliteWrapper)
    maxParallelForks = 1
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    doFirst {
        addTestListener(JazzerSupportTestPulseListener(supportTestClassCount))
    }
}

tasks.named("check") {
    dependsOn(jazzerRegression)
}

tasks.register("fuzzAllLocal") {
    description = "Runs all active local-only FinGrind fuzzing tasks."
    group = "verification"
    dependsOn(
        fuzzCliRequest,
        fuzzPostingWorkflow,
        fuzzSqliteBookRoundTrip
    )
}

fuzzPostingWorkflow.configure {
    mustRunAfter(fuzzCliRequest)
}

fuzzSqliteBookRoundTrip.configure {
    mustRunAfter(fuzzPostingWorkflow)
}

val jazzerSupportTests = tasks.named<Test>("test")

regressionCliRequest.configure {
    mustRunAfter(jazzerSupportTests)
}

regressionPostingWorkflow.configure {
    mustRunAfter(regressionCliRequest)
    mustRunAfter(jazzerSupportTests)
}

regressionSqliteBookRoundTrip.configure {
    mustRunAfter(regressionPostingWorkflow)
    mustRunAfter(jazzerSupportTests)
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
