plugins {
    `java-library`
    id("fingrind.java-conventions")
}

description = "SQLite-backed FinGrind persistence adapter"

dependencies {
    implementation(project(":core"))
    implementation(project(":runtime"))
    implementation(libs.jackson.databind)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
