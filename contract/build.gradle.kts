plugins {
    `java-library`
    id("fingrind.java-conventions")
}

description = "Canonical FinGrind public contract model and protocol metadata"

dependencies {
    api(project(":core"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
