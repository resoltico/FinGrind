import dev.erst.fingrind.buildlogic.DistributionContractReader
import dev.erst.fingrind.buildlogic.DistributionBundleTargetReader
import dev.erst.fingrind.buildlogic.addOpens
import dev.erst.fingrind.buildlogic.addReads
import dev.erst.fingrind.buildlogic.patchModule
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    `java-library`
    `java-test-fixtures`
    id("dev.erst.fingrind.java-conventions")
    id("dev.erst.fingrind.managed-sqlite-consumer")
}

description = "SQLite-backed FinGrind persistence adapter"

val repositoryRootDirectory = rootProject.projectDir.toPath()
val hostBundleTarget = DistributionBundleTargetReader.hostBundleTarget(repositoryRootDirectory)
val managedSqliteToolchainFingerprintPath =
    rootProject.layout.buildDirectory.file(
        "managed-sqlite/${hostBundleTarget.classifier}/toolchain-fingerprint.json",
    )
val managedSqliteBuildContractPath =
    rootProject.layout.buildDirectory.file(
        "managed-sqlite/${hostBundleTarget.classifier}/build-contract.json",
    )
val protectedBookFixturePath =
    project.layout.projectDirectory.dir("src/test/resources/dev/erst/fingrind/sqlite/fixtures")
val sqliteWhiteBoxTestPatchPath =
    files(
        sourceSets["main"].output.resourcesDir,
        sourceSets["test"].output,
        sourceSets["testFixtures"].output,
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
    description = "Regenerates the committed protected-book compatibility fixture family."
    classpath =
        files(
            sourceSets["test"].output.classesDirs,
            sourceSets["testFixtures"].output,
            sourceSets["main"].output,
            configurations.testRuntimeClasspath,
        )
    mainClass.set("dev.erst.fingrind.sqlite.SqliteProtectedBookFixtureGenerator")
    args(protectedBookFixturePath.asFile.absolutePath)
    patchModule("dev.erst.fingrind.sqlite", sqliteWhiteBoxTestPatchPath)
    addReads("dev.erst.fingrind.sqlite", "ALL-UNNAMED")
    addOpens("dev.erst.fingrind.sqlite", "dev.erst.fingrind.sqlite", "ALL-UNNAMED")
}

tasks.named<Test>("test") {
    patchModule("dev.erst.fingrind.sqlite", sqliteWhiteBoxTestPatchPath)
    addReads("dev.erst.fingrind.sqlite", "ALL-UNNAMED")
    addOpens("dev.erst.fingrind.sqlite", "dev.erst.fingrind.sqlite", "ALL-UNNAMED")
}

tasks.named<Pmd>("pmdTest") {}
