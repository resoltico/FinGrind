plugins {
    `java-library`
    `java-test-fixtures`
    id("dev.erst.fingrind.java-conventions")
}

description = "FinGrind execution boundary and book workflow orchestration"

dependencies {
    api(project(":contract"))
    api(project(":core"))
    testFixturesApi(project(":contract"))
    testFixturesImplementation(project(":contract"))
    testFixturesImplementation(project(":core"))
}
