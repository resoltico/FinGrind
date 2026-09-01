plugins {
    `java-library`
    `java-test-fixtures`
    id("dev.erst.fingrind.java-conventions")
    id("dev.erst.fingrind.production-mutation-scope")
}

description = "FinGrind execution boundary and book workflow orchestration"

tasks.named("pitest") {
    mustRunAfter(":core:pitest")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

dependencies {
    api(project(":contract"))
    api(project(":core"))
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesApi(project(":contract"))
    testFixturesImplementation(project(":contract"))
    testFixturesImplementation(project(":core"))
}
