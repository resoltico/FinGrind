package dev.erst.fingrind.buildlogic

import org.gradle.api.Project

internal fun Project.configureFinGrindArtifactRepositories() {
    repositories.mavenCentral()
}
