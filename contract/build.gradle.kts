import org.gradle.api.tasks.WriteProperties

plugins {
    `java-library`
    id("fingrind.java-conventions")
}

description = "Canonical FinGrind public contract model and protocol metadata"
val fingrindJavaVersion = providers.gradleProperty("fingrindJavaVersion").get()

dependencies {
    api(project(":core"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val generatedProtocolResourcesDirectory =
    layout.buildDirectory.dir("generated-resources/protocol")
val writeRuntimeEnvironmentContract =
    tasks.register<WriteProperties>("writeRuntimeEnvironmentContract") {
        destinationFile.set(
            generatedProtocolResourcesDirectory.map { directory ->
                directory.file("dev/erst/fingrind/contract/protocol/runtime-environment-contract.properties")
            },
        )
        property("sourceCheckoutJava", "$fingrindJavaVersion+")
    }

sourceSets {
    named("main") {
        resources.srcDir(generatedProtocolResourcesDirectory)
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(writeRuntimeEnvironmentContract)
}
