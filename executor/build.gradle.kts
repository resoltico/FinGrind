plugins {
    `java-library`
    `java-test-fixtures`
    id("fingrind.java-conventions")
}

description = "FinGrind execution boundary and book workflow orchestration"

dependencies {
    api(project(":contract"))
    api(project(":core"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
