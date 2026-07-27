import dev.erst.fingrind.buildlogic.FinGrindBuildMetadata
import dev.erst.fingrind.buildlogic.WriteRuntimeEnvironmentContractTask
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

plugins {
    `java-library`
    id("dev.erst.fingrind.java-conventions")
}

description = "Canonical FinGrind public contract model and protocol metadata"
val fingrindJavaVersion = FinGrindBuildMetadata.load(project).javaVersion

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

tasks.register<JavaExec>("syncUserInstallDocs") {
    group = "documentation"
    description =
        "Synchronizes docs/USER_INSTALL.md and docs/USER_QUICK_START.md generated install blocks from the canonical publication contracts."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.erst.fingrind.contract.protocol.ProtocolUserInstallDocumentSyncMain")
    args(rootProject.layout.projectDirectory.asFile.absolutePath)
}

tasks.register<JavaExec>("syncCapabilityBaseline") {
    group = "verification"
    description =
        "Synchronizes the release-smoke capability baseline from the canonical protocol catalog."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.erst.fingrind.contract.discovery.ProtocolCapabilityBaselineSyncMain")
    args(
        rootProject.layout.projectDirectory
            .file(
                "contract/src/main/resources/dev/erst/fingrind/contract/protocol/capability-baseline",
            )
            .asFile
            .absolutePath,
    )
}

tasks.named<Test>("test") {
    systemProperty("fingrind.repository.root", rootProject.layout.projectDirectory.asFile.absolutePath)
    inputs.file(rootProject.layout.projectDirectory.file("README.md"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("docs"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("cli/src/bundle/root"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
