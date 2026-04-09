plugins {
    application
    id("fingrind.java-conventions")
    alias(libs.plugins.shadow)
}

description = "CLI transport adapter for the FinGrind application boundary"

dependencies {
    implementation(project(":application"))
    implementation(project(":core"))
    implementation(project(":runtime"))
    implementation(project(":sqlite"))
    implementation(libs.jackson.databind)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "dev.erst.fingrind.cli.App"
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    environment("FINGRIND_SQLITE3_BINARY", rootProject.file("scripts/sqlite3.sh").absolutePath)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName = "fingrind"
    archiveVersion = ""
    archiveClassifier = ""

    // Merge ServiceLoader registrations from all bundled JARs.
    mergeServiceFiles()

    // Exclude per-dependency META-INF license and notice files to prevent conflicts
    // and silent overwrites. FinGrind bundles its own curated NOTICE, MIT LICENSE,
    // and the Apache License 2.0 text that covers the bundled Jackson components.
    exclude("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/LICENSE.md")
    exclude("META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/NOTICE.md")
    exclude("META-INF/DEPENDENCIES")

    // Bundle the curated attribution notice and both license texts into META-INF/.
    // NOTICE covers bundled dependency attribution for the CLI distribution.
    // LICENSE is the MIT license for FinGrind's own code.
    // LICENSE-APACHE-2.0 satisfies Apache License 2.0 Section 4(a) for bundled Jackson artifacts.
    from(rootProject.file("NOTICE")) { into("META-INF") }
    from(rootProject.file("LICENSE")) { into("META-INF") }
    from(rootProject.file("LICENSE-APACHE-2.0")) { into("META-INF") }

    manifest {
        attributes(
            "Implementation-Title" to "FinGrind",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Ervins Strauhmanis",
            "Implementation-License" to "MIT",
        )
    }
}

tasks.named<ProcessResources>("processResources") {
    val description: String = providers.gradleProperty("fingrindDescription").get()
    val sqliteVersion: String = providers.gradleProperty("fingrindSqliteVersion").get()
    val version: String = project.version.toString()
    inputs.property("fingrindDescription", description)
    inputs.property("fingrindSqliteVersion", sqliteVersion)
    inputs.property("fingrindVersion", version)
    filesMatching("fingrind.properties") {
        expand(
            mapOf(
                "fingrindDescription" to description,
                "fingrindSqliteVersion" to sqliteVersion,
                "version" to version
            )
        )
    }
}

tasks.named("assemble") {
    dependsOn("shadowJar")
}
