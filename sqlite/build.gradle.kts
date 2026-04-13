plugins {
    `java-library`
    id("fingrind.java-conventions")
}

description = "SQLite-backed FinGrind persistence adapter"

dependencies {
    implementation(project(":application"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
