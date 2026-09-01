plugins {
    `java-library`
    `java-test-fixtures`
    id("dev.erst.fingrind.java-conventions")
    id("dev.erst.fingrind.production-mutation-scope")
}

description = "Core FinGrind accounting domain model"

dependencies {
    implementation(libs.jackson.databind)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
