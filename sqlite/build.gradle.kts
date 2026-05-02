plugins {
    `java-library`
    `java-test-fixtures`
    id("dev.erst.fingrind.java-conventions")
}

description = "SQLite-backed FinGrind persistence adapter"

dependencies {
    implementation(project(":contract"))
    implementation(project(":executor"))
    testImplementation(libs.jackson.databind)
    testFixturesImplementation(project(":executor"))
}
