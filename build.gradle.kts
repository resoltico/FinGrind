import com.diffplug.gradle.spotless.SpotlessExtension
import dev.erst.fingrind.build.PrepareManagedSqliteTask
import dev.erst.fingrind.build.VerifyManagedSqliteSourceTask
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.GradleException
import org.gradle.api.tasks.JavaExec
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.io.File

plugins {
    base
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.shadow) apply false
}

description = providers.gradleProperty("fingrindDescription").get()

val fingrindManagedSqliteVersion = providers.gradleProperty("fingrindManagedSqliteVersion").get()
val fingrindManagedSqliteAmalgamationId =
    providers.gradleProperty("fingrindManagedSqliteAmalgamationId").get()
val fingrindManagedSqliteSourceSha3 =
    providers.gradleProperty("fingrindManagedSqliteSourceSha3").get()

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

val managedSqliteOperatingSystemId = managedSqliteOperatingSystemId()
val managedSqliteArchitectureId =
    System.getProperty("os.arch", "unknown").lowercase().replace(Regex("[^a-z0-9]+"), "-")
val managedSqliteClassifier = "$managedSqliteOperatingSystemId-$managedSqliteArchitectureId"
val managedSqliteLibraryFileName = managedSqliteLibraryFileName(managedSqliteOperatingSystemId)
val managedSqliteSourceDirectory =
    layout.projectDirectory.dir("third_party/sqlite/sqlite-amalgamation-$fingrindManagedSqliteAmalgamationId")
val managedSqliteSourceFile = managedSqliteSourceDirectory.file("sqlite3.c")
val managedSqliteHeaderFile = managedSqliteSourceDirectory.file("sqlite3.h")
val managedSqliteExtensionHeaderFile = managedSqliteSourceDirectory.file("sqlite3ext.h")
val managedSqliteCompiler =
    providers.environmentVariable("CC").orNull
        ?.takeIf { candidate -> !candidate.contains("/") || File(candidate).isFile }
        ?: "cc"
val managedSqliteLibraryPath =
    layout.buildDirectory.file("managed-sqlite/$managedSqliteClassifier/$managedSqliteLibraryFileName")
val managedSqliteSourceFileValue = managedSqliteSourceFile.asFile
val managedSqliteHeaderFileValue = managedSqliteHeaderFile.asFile
val managedSqliteExtensionHeaderFileValue = managedSqliteExtensionHeaderFile.asFile
val managedSqliteLibraryFileValue = managedSqliteLibraryPath.get().asFile

val verifyManagedSqliteSource =
    tasks.register<VerifyManagedSqliteSourceTask>("verifyManagedSqliteSource") {
        group = "build setup"
        description =
            "Verifies the vendored SQLite amalgamation matches the pinned upstream 3.53.0 source hash."
        sourceFile.set(managedSqliteSourceFileValue)
        expectedSha3.set(fingrindManagedSqliteSourceSha3)
    }

val prepareManagedSqlite =
    tasks.register<PrepareManagedSqliteTask>("prepareManagedSqlite") {
        group = "build setup"
        description =
            "Builds the managed SQLite ${fingrindManagedSqliteVersion} shared library for the current host."

        dependsOn(verifyManagedSqliteSource)
        sourceFile.set(managedSqliteSourceFileValue)
        supportFiles.from(managedSqliteHeaderFileValue, managedSqliteExtensionHeaderFileValue)
        compiler.set(managedSqliteCompiler)
        operatingSystemId.set(managedSqliteOperatingSystemId)
        sqliteVersion.set(fingrindManagedSqliteVersion)
        outputFile.set(managedSqliteLibraryFileValue)
    }

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

        tasks.withType<Test>().configureEach {
            dependsOn(rootProject.tasks.named("prepareManagedSqlite"))
            environment("FINGRIND_SQLITE_LIBRARY", rootProject.layout.buildDirectory.file("managed-sqlite/$managedSqliteClassifier/$managedSqliteLibraryFileName").get().asFile.absolutePath)
        }

        tasks.withType<JavaExec>().configureEach {
            dependsOn(rootProject.tasks.named("prepareManagedSqlite"))
            environment("FINGRIND_SQLITE_LIBRARY", rootProject.layout.buildDirectory.file("managed-sqlite/$managedSqliteClassifier/$managedSqliteLibraryFileName").get().asFile.absolutePath)
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
