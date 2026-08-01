plugins {
    `java-library`
    id("dev.erst.fingrind.java-conventions")
}

description = "Independent architecture verification for FinGrind production modules"

dependencies {
    testImplementation(libs.archunit.junit5)
    testImplementation(project(":core"))
    testImplementation(project(":contract"))
    testImplementation(project(":executor"))
    testImplementation(project(":sqlite"))
    testImplementation(project(":report-pdf"))
    testImplementation(project(":cli"))
}
