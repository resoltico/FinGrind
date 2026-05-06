import dev.erst.fingrind.buildlogic.DistributionContractReader

plugins {
    `java-library`
    `java-test-fixtures`
    id("dev.erst.fingrind.java-conventions")
}

description = "SQLite-backed FinGrind persistence adapter"

val repositoryRootDirectory = rootProject.projectDir.toPath()
val hostBundleTarget = DistributionContractReader.hostBundleTarget(repositoryRootDirectory)
val managedSqliteDigestPath =
    rootProject.layout.buildDirectory.file(
        "managed-sqlite/${hostBundleTarget.classifier}/${hostBundleTarget.sqliteLibraryFileName}.sha256",
    )

dependencies {
    implementation(project(":contract"))
    implementation(project(":executor"))
    testImplementation(libs.jackson.databind)
    testFixturesImplementation(project(":executor"))
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(rootProject.tasks.named("prepareManagedSqlite"))
    from(managedSqliteDigestPath) {
        into("META-INF/fingrind")
        rename { "managed-sqlite.sha256" }
    }
}
