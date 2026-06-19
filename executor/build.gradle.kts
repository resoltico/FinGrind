plugins {
    `java-library`
    `java-test-fixtures`
    id("dev.erst.fingrind.java-conventions")
}

description = "FinGrind execution boundary and book workflow orchestration"

dependencies {
    api(project(":contract"))
    api(project(":core"))
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesApi(project(":contract"))
    testFixturesImplementation(project(":contract"))
    testFixturesImplementation(project(":core"))
}
