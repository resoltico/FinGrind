package dev.erst.fingrind.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

internal fun Project.configurePinnedJacocoVersion() {
    val jacocoVersion = versionCatalog().version("jacoco")
    extensions.configure(JacocoPluginExtension::class.java) {
        toolVersion = jacocoVersion
    }
}
