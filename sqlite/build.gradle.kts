import dev.erst.fingrind.buildlogic.DistributionContractReader
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    `java-library`
    `java-test-fixtures`
    id("dev.erst.fingrind.java-conventions")
}

description = "SQLite-backed FinGrind persistence adapter"

val repositoryRootDirectory = rootProject.projectDir.toPath()
val hostBundleTarget = DistributionContractReader.hostBundleTarget(repositoryRootDirectory)
val managedSqliteToolchainFingerprintPath =
    rootProject.layout.buildDirectory.file(
        "managed-sqlite/${hostBundleTarget.classifier}/toolchain-fingerprint.json",
    )
val managedSqliteBuildContractPath =
    rootProject.layout.buildDirectory.file(
        "managed-sqlite/${hostBundleTarget.classifier}/build-contract.json",
    )
val protectedBookFixturePath =
    project.layout.projectDirectory.file(
        "src/test/resources/dev/erst/fingrind/sqlite/fixtures/current-default-protected-book.sqlite",
    )
val protectedBookFixtureMetadataPath =
    project.layout.projectDirectory.file(
        "src/test/resources/dev/erst/fingrind/sqlite/fixtures/current-default-protected-book.metadata.json",
    )

dependencies {
    implementation(libs.jackson.databind)
    implementation(project(":contract"))
    implementation(project(":executor"))
    testImplementation(libs.jackson.databind)
    testFixturesImplementation(project(":executor"))
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(rootProject.tasks.named("prepareManagedSqlite"))
    from(managedSqliteToolchainFingerprintPath) {
        into("META-INF/fingrind")
        rename { "managed-sqlite-toolchain.json" }
    }
    from(managedSqliteBuildContractPath) {
        into("META-INF/fingrind")
        rename { "managed-sqlite-build-contract.json" }
    }
}

tasks.register<JavaExec>("refreshProtectedBookFixture") {
    group = "verification"
    description = "Regenerates the committed protected-book compatibility fixture and metadata."
    classpath =
        files(
            sourceSets["test"].output.classesDirs,
            sourceSets["testFixtures"].output,
            sourceSets["main"].output,
            configurations.testRuntimeClasspath,
        )
    mainClass.set("dev.erst.fingrind.sqlite.SqliteProtectedBookFixtureGenerator")
    args(
        protectedBookFixturePath.asFile.absolutePath,
        protectedBookFixtureMetadataPath.asFile.absolutePath,
    )
}

val stageRefreshedProtectedBookFixtureForTestRuntime =
    tasks.register<Sync>("stageRefreshedProtectedBookFixtureForTestRuntime") {
        dependsOn(tasks.named("processTestResources"))
        mustRunAfter(tasks.named("refreshProtectedBookFixture"))
        from(protectedBookFixturePath, protectedBookFixtureMetadataPath)
        into(layout.buildDirectory.dir("resources/test/dev/erst/fingrind/sqlite/fixtures"))
    }

tasks.named("refreshProtectedBookFixture") {
    finalizedBy(stageRefreshedProtectedBookFixtureForTestRuntime)
}

tasks.named<Test>("test") {
    dependsOn(stageRefreshedProtectedBookFixtureForTestRuntime)
}

tasks.named<Pmd>("pmdTest") {
    dependsOn(stageRefreshedProtectedBookFixtureForTestRuntime)
}
