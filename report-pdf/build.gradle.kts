plugins {
    `java-library`
    id("fingrind.java-conventions")
}

description = "PDF report rendering adapter for FinGrind reporting results"

dependencies {
    api(project(":contract"))
    implementation(libs.pdfbox)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
