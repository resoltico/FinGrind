plugins {
    `java-library`
    `java-test-fixtures`
    id("fingrind.java-conventions")
}

description = "Core FinGrind accounting domain model"

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
