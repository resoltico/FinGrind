plugins {
    `java-library`
    `java-test-fixtures`
    id("fingrind.java-conventions")
}

description = "FinGrind application boundary and write workflows"

dependencies {
    api(project(":core"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
