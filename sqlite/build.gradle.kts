plugins {
    `java-library`
    id("fingrind.java-conventions")
}

description = "SQLite-backed FinGrind persistence adapter"

dependencies {
    implementation(project(":contract"))
    implementation(project(":executor"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
