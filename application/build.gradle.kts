plugins {
    `java-library`
    id("fingrind.java-conventions")
}

description = "FinGrind application boundary and write workflows"

dependencies {
    implementation(project(":core"))
    implementation(project(":runtime"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
