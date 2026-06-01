import dev.erst.fingrind.buildlogic.DistributionTextRendering
import dev.erst.fingrind.buildlogic.FinGrindBuildMetadata
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

plugins {
    `java-library`
    id("dev.erst.fingrind.java-conventions")
}

description = "Canonical FinGrind public contract model and protocol metadata"
val fingrindJavaVersion = FinGrindBuildMetadata.load(project).javaVersion

abstract class WriteRuntimeEnvironmentContractTask : DefaultTask() {
    @get:Input
    abstract val sourceCheckoutJava: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeContract() {
        val destination = outputFile.get().asFile
        destination.parentFile.mkdirs()
        destination.writeText(
            """
            {
              "sourceCheckoutJava": ${DistributionTextRendering.jsonString(sourceCheckoutJava.get())}
            }
            """
                .trimIndent()
                + System.lineSeparator(),
        )
    }
}

dependencies {
    api(project(":core"))
    implementation(libs.jackson.databind)
}

val generatedProtocolResourcesDirectory =
    layout.buildDirectory.dir("generated-resources/protocol")
val writeRuntimeEnvironmentContract =
    tasks.register<WriteRuntimeEnvironmentContractTask>("writeRuntimeEnvironmentContract") {
        sourceCheckoutJava.set("${fingrindJavaVersion}+")
        outputFile.set(
            generatedProtocolResourcesDirectory.map { directory ->
                directory.file("dev/erst/fingrind/contract/protocol/runtime-environment-contract.json")
            },
        )
    }

sourceSets {
    named("main") {
        resources.srcDir(generatedProtocolResourcesDirectory)
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(writeRuntimeEnvironmentContract)
}

tasks.register<JavaExec>("syncUserCliDocs") {
    group = "documentation"
    description =
        "Synchronizes docs/USER_CLI.md generated command-table blocks from the canonical protocol catalog."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.erst.fingrind.contract.protocol.ProtocolUserCliDocumentSyncMain")
    args(rootProject.layout.projectDirectory.file("docs/USER_CLI.md").asFile.absolutePath)
}

tasks.named<Test>("test") {
    inputs.file(rootProject.layout.projectDirectory.file("README.md"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("docs"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("cli/src/bundle/root"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
