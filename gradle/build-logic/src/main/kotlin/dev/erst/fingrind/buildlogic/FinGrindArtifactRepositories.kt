package dev.erst.fingrind.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.maven

private const val jacocoSnapshotRepositoryUrl =
    "https://central.sonatype.com/repository/maven-snapshots/"

internal fun Project.configureFinGrindArtifactRepositories() {
    repositories.mavenCentral()
    repositories.maven(jacocoSnapshotRepositoryUrl) {
        name = "jacocoSnapshots"
        mavenContent {
            snapshotsOnly()
        }
        content {
            includeGroup("org.jacoco")
        }
    }
}
