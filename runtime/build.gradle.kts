plugins {
    `java-library`
    id("fingrind.java-conventions")
}

description = "FinGrind runtime ports and in-memory adapters"

dependencies {
    api(project(":core"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
