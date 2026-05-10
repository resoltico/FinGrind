import dev.erst.fingrind.buildlogic.DistributionContractReader
import org.gradle.api.tasks.JavaExec

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

tasks.register<JavaExec>("refreshProtectedBookFixture") {
    group = "verification"
    description = "Regenerates the committed protected-book compatibility fixture and metadata."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.erst.fingrind.sqlite.SqliteProtectedBookFixtureGenerator")
    args(
        project.layout.projectDirectory
            .file("src/test/resources/dev/erst/fingrind/sqlite/fixtures/current-default-protected-book.sqlite")
            .asFile
            .absolutePath,
        project.layout.projectDirectory
            .file("src/test/resources/dev/erst/fingrind/sqlite/fixtures/current-default-protected-book.metadata.json")
            .asFile
            .absolutePath,
    )
}
