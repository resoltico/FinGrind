import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Comparator
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import javax.inject.Inject

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

@CacheableTask
abstract class VerifyManagedSqliteSourceTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFile: RegularFileProperty

    @get:Input
    abstract val expectedSha3: Property<String>

    @TaskAction
    fun verify() {
        val sqliteSource = sourceFile.get().asFile
        if (!sqliteSource.isFile) {
            throw GradleException("Missing vendored SQLite source at ${sqliteSource.absolutePath}")
        }
        val actualSourceSha3 =
            MessageDigest.getInstance("SHA3-256")
                .digest(sqliteSource.readBytes())
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
        if (actualSourceSha3 != expectedSha3.get()) {
            throw GradleException(
                "Vendored SQLite source hash mismatch. Expected ${expectedSha3.get()} but found $actualSourceSha3 for ${sqliteSource.absolutePath}.",
            )
        }
    }
}

@CacheableTask
abstract class PrepareManagedSqliteTask
    @Inject
    constructor(
        private val execOperations: ExecOperations,
    ) : DefaultTask() {
        @get:InputFile
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val sourceFile: RegularFileProperty

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val supportFiles: ConfigurableFileCollection

        @get:Input
        abstract val compiler: Property<String>

        @get:Input
        abstract val operatingSystemId: Property<String>

        @get:Input
        abstract val sqliteVersion: Property<String>

        @get:OutputFile
        abstract val outputFile: RegularFileProperty

        @TaskAction
        fun compile() {
            val outputLibraryFile = outputFile.get().asFile
            outputLibraryFile.parentFile.mkdirs()
            execOperations.exec {
                commandLine(
                    buildCommandLine(
                        compiler = compiler.get(),
                        operatingSystemId = operatingSystemId.get(),
                        sqliteVersion = sqliteVersion.get(),
                        sourceFilePath = sourceFile.get().asFile.absolutePath,
                        outputFilePath = outputLibraryFile.absolutePath,
                    ),
                )
            }
        }

        private fun buildCommandLine(
            compiler: String,
            operatingSystemId: String,
            sqliteVersion: String,
            sourceFilePath: String,
            outputFilePath: String,
        ): List<String> =
            buildList {
                add(compiler)
                add("-O2")
                add("-fPIC")
                add("-DSQLITE_THREADSAFE=1")
                add("-DSQLITE_OMIT_LOAD_EXTENSION=1")
                if (operatingSystemId == "macos") {
                    add("-dynamiclib")
                    add("-current_version")
                    add(sqliteVersion)
                    add("-compatibility_version")
                    add(sqliteVersion)
                } else {
                    add("-shared")
                    add("-Wl,-soname,libsqlite3.so.0")
                }
                add("-o")
                add(outputFilePath)
                add(sourceFilePath)
                if (operatingSystemId == "linux") {
                    add("-ldl")
                    add("-lpthread")
                }
            }
    }

class JazzerSupportTestPulseListener(
    private val totalClasses: Int,
    pulseIntervalSeconds: Long = 15,
) : TestListener {
    private val pulseExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "fingrind-jazzer-support-pulse").apply {
                isDaemon = true
            }
        }
    private val lock = Any()
    private val pulseIntervalSeconds = pulseIntervalSeconds.coerceAtLeast(1)

    private var rootStarted = false
    private var rootFinished = false
    private var completedClasses = 0
    private var completedTests = 0
    private var activeTopLevelClass: String? = null
    private var activeClassTests = 0
    private var activeClassFailedTests = 0
    private var activeClassSkippedTests = 0
    private var activeTestClass: String? = null
    private var activeTestName: String? = null

    override fun beforeSuite(suite: TestDescriptor) {
        if (suite.parent != null) {
            return
        }
        synchronized(lock) {
            if (rootStarted) {
                return
            }
            rootStarted = true
            emit("support-tests phase=start total-classes=$totalClasses")
            pulseExecutor.scheduleAtFixedRate(
                { emitHeartbeat() },
                pulseIntervalSeconds,
                pulseIntervalSeconds,
                TimeUnit.SECONDS,
            )
        }
    }

    override fun afterSuite(suite: TestDescriptor, result: TestResult) {
        if (suite.parent != null) {
            return
        }
        synchronized(lock) {
            finishActiveClass()
            emit(
                "support-tests phase=finish completed-classes=$completedClasses/$totalClasses completed-tests=$completedTests result=${result.resultType}"
            )
            rootFinished = true
        }
        pulseExecutor.shutdownNow()
    }

    override fun beforeTest(testDescriptor: TestDescriptor) {
        val topLevelClass = topLevelClassName(testDescriptor.className) ?: return
        synchronized(lock) {
            if (activeTopLevelClass == null) {
                startClass(topLevelClass)
            } else if (activeTopLevelClass != topLevelClass) {
                finishActiveClass()
                startClass(topLevelClass)
            }
            activeTestClass = testDescriptor.className
            activeTestName = testDescriptor.name
        }
    }

    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
        synchronized(lock) {
            completedTests += 1
            activeClassTests += 1
            when (result.resultType) {
                TestResult.ResultType.FAILURE -> activeClassFailedTests += 1
                TestResult.ResultType.SKIPPED -> activeClassSkippedTests += 1
                TestResult.ResultType.SUCCESS -> Unit
            }

            emit(
                "support-tests phase=test-complete completed-tests=$completedTests class=${pulseValue(testDescriptor.className)} name=${pulseValue(testDescriptor.name)} result=${result.resultType}"
            )

            if (activeTestClass == testDescriptor.className && activeTestName == testDescriptor.name) {
                activeTestClass = null
                activeTestName = null
            }
        }
    }

    private fun emitHeartbeat() {
        val heartbeat =
            synchronized(lock) {
                if (!rootStarted || rootFinished) {
                    return
                }
                val className = activeTestClass ?: activeTopLevelClass ?: return
                buildString {
                    append("support-tests phase=test-progress")
                    append(" completed-tests=").append(completedTests)
                    append(" completed-classes=").append(completedClasses).append('/').append(totalClasses)
                    append(" class=").append(pulseValue(className))
                    activeTestName?.let { testName ->
                        append(" name=").append(pulseValue(testName))
                    }
                }
            }
        emit(heartbeat)
    }

    private fun startClass(topLevelClass: String) {
        activeTopLevelClass = topLevelClass
        activeClassTests = 0
        activeClassFailedTests = 0
        activeClassSkippedTests = 0
        emit("support-tests phase=class-start class=${pulseValue(topLevelClass)}")
    }

    private fun finishActiveClass() {
        val topLevelClass = activeTopLevelClass ?: return
        completedClasses += 1
        emit(
            "support-tests phase=class-complete completed-classes=$completedClasses/$totalClasses class=${pulseValue(topLevelClass)} result=${activeClassResult()}"
        )
        activeTopLevelClass = null
        activeClassTests = 0
        activeClassFailedTests = 0
        activeClassSkippedTests = 0
        activeTestClass = null
        activeTestName = null
    }

    private fun activeClassResult(): TestResult.ResultType =
        when {
            activeClassFailedTests > 0 -> TestResult.ResultType.FAILURE
            activeClassTests > 0 && activeClassSkippedTests == activeClassTests -> TestResult.ResultType.SKIPPED
            else -> TestResult.ResultType.SUCCESS
        }

    private fun topLevelClassName(className: String?): String? = className?.substringBefore('$')

    private fun pulseValue(value: String?): String = value?.replace(Regex("\\s+"), "_") ?: "unknown"

    private fun emit(message: String) {
        println("[JAZZER-PULSE] $message")
    }
}

val fingrindJavaVersion: Int =
    providers.gradleProperty("fingrindJavaVersion").map(String::toInt).get()
val repoRootDirectory = layout.projectDirectory.dir("..")
val sharedGradleProperties =
    Properties().apply {
        repoRootDirectory.file("gradle.properties").asFile.inputStream().use(::load)
    }
val jazzerMaxDuration = providers.gradleProperty("jazzerMaxDuration").orNull
val jazzerMaxExecutions = providers.gradleProperty("jazzerMaxExecutions").orNull

fun sharedFingrindProperty(name: String): String =
    sharedGradleProperties.getProperty(name)
        ?: throw GradleException("Missing shared FinGrind property '$name' in ../gradle.properties.")

fun managedSqliteOperatingSystemId(): String {
    val operatingSystem = System.getProperty("os.name", "").lowercase()
    if (operatingSystem.contains("mac")) {
        return "macos"
    }
    if (operatingSystem.contains("linux")) {
        return "linux"
    }
    throw GradleException(
        "FinGrind's managed SQLite build currently supports macOS and Linux only. Detected: ${System.getProperty("os.name")}",
    )
}

fun managedSqliteLibraryFileName(operatingSystemId: String): String =
    when (operatingSystemId) {
        "macos" -> "libsqlite3.dylib"
        "linux" -> "libsqlite3.so.0"
        else -> throw GradleException("Unsupported managed SQLite operating system id: $operatingSystemId")
    }

val fingrindManagedSqliteVersion = sharedFingrindProperty("fingrindManagedSqliteVersion")
val fingrindManagedSqliteAmalgamationId = sharedFingrindProperty("fingrindManagedSqliteAmalgamationId")
val fingrindManagedSqliteSourceSha3 = sharedFingrindProperty("fingrindManagedSqliteSourceSha3")
val managedSqliteOperatingSystemId = managedSqliteOperatingSystemId()
val managedSqliteArchitectureId =
    System.getProperty("os.arch", "unknown").lowercase().replace(Regex("[^a-z0-9]+"), "-")
val managedSqliteClassifier = "$managedSqliteOperatingSystemId-$managedSqliteArchitectureId"
val managedSqliteLibraryFileName = managedSqliteLibraryFileName(managedSqliteOperatingSystemId)
val managedSqliteSourceDirectory =
    repoRootDirectory.dir("third_party/sqlite/sqlite-amalgamation-$fingrindManagedSqliteAmalgamationId")
val managedSqliteSourceFile = managedSqliteSourceDirectory.file("sqlite3.c").asFile
val managedSqliteHeaderFile = managedSqliteSourceDirectory.file("sqlite3.h").asFile
val managedSqliteExtensionHeaderFile = managedSqliteSourceDirectory.file("sqlite3ext.h").asFile
val managedSqliteCompiler =
    providers.environmentVariable("CC").orNull
        ?.takeIf { candidate -> !candidate.contains("/") || file(candidate).isFile }
        ?: "cc"
val managedSqliteLibraryPath =
    layout.buildDirectory.file("managed-sqlite/$managedSqliteClassifier/$managedSqliteLibraryFileName")
val managedSqliteLibraryAbsolutePath = managedSqliteLibraryPath.get().asFile.absolutePath

val verifyManagedSqliteSource =
    tasks.register<VerifyManagedSqliteSourceTask>("verifyManagedSqliteSource") {
        group = "build setup"
        description =
            "Verifies the vendored SQLite amalgamation matches the pinned upstream source hash."
        sourceFile.set(managedSqliteSourceFile)
        expectedSha3.set(fingrindManagedSqliteSourceSha3)
    }

val prepareManagedSqlite =
    tasks.register<PrepareManagedSqliteTask>("prepareManagedSqlite") {
        group = "build setup"
        description =
            "Builds the managed SQLite $fingrindManagedSqliteVersion shared library for the current host."
        dependsOn(verifyManagedSqliteSource)
        sourceFile.set(managedSqliteSourceFile)
        supportFiles.from(managedSqliteHeaderFile, managedSqliteExtensionHeaderFile)
        compiler.set(managedSqliteCompiler)
        operatingSystemId.set(managedSqliteOperatingSystemId)
        sqliteVersion.set(fingrindManagedSqliteVersion)
        outputFile.set(managedSqliteLibraryPath)
    }

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
    jvmArgs("--enable-native-access=ALL-UNNAMED")
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
    maxParallelForks = 1
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    doFirst {
        addTestListener(JazzerSupportTestPulseListener(supportTestClassCount))
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(prepareManagedSqlite)
    environment("FINGRIND_SQLITE_LIBRARY", managedSqliteLibraryAbsolutePath)
}

tasks.withType<JavaExec>().configureEach {
    dependsOn(prepareManagedSqlite)
    environment("FINGRIND_SQLITE_LIBRARY", managedSqliteLibraryAbsolutePath)
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
