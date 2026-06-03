package dev.erst.fingrind.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
import org.gradle.api.Project
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

internal fun Project.configureJavaQualityConventions() {
    val libs = versionCatalog()
    val sourcePolicyTask = registerJavaSourcePolicyTask()
    val sourceShapeTask = registerJavaSourceShapeTask()
    val sourceDuplicationTask = registerJavaSourceDuplicationTask()
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

    tasks.named("check") {
        dependsOn("spotlessCheck")
        dependsOn("jacocoTestCoverageVerification")
        dependsOn(sourcePolicyTask)
        dependsOn(sourceShapeTask)
        dependsOn(sourceDuplicationTask)
        dependsOn(jacksonDependencyPolicyTask)
    }
}
