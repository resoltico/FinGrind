import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    id("dev.erst.fingrind.java-conventions")
    id("dev.erst.fingrind.managed-sqlite-consumer")
    alias(libs.plugins.shadow)
    id("dev.erst.fingrind.cli-distribution")
}

description = "CLI transport adapter for the FinGrind execution boundary"

dependencies {
    testImplementation(libs.archunit.junit5)
    implementation(project(":contract"))
    implementation(project(":core"))
    implementation(project(":executor"))
    implementation(project(":report-pdf"))
    implementation(project(":sqlite"))
    implementation(libs.jackson.databind)
}

application {
    mainModule = "dev.erst.fingrind.cli"
    mainClass = "dev.erst.fingrind.cli.App"
}

val buildMetadata = dev.erst.fingrind.buildlogic.FinGrindBuildMetadata.load(project)

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName = "fingrind"
    archiveVersion = ""
    archiveClassifier = ""
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    inputs.property("shadowJarImplementationTitle", "FinGrind")
    inputs.property("shadowJarImplementationVersion", project.version.toString())
    inputs.property("shadowJarImplementationVendor", buildMetadata.implementationVendor)
    inputs.property("shadowJarImplementationLicense", buildMetadata.implementationLicense)

    // Merge ServiceLoader registrations from all bundled JARs.
    mergeServiceFiles()

    // Exclude per-dependency META-INF license and notice files to prevent conflicts
    // and silent overwrites. FinGrind bundles its own curated NOTICE, MIT LICENSE,
    // and the Apache License 2.0 text that covers bundled Apache-licensed components.
    exclude("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/LICENSE.md")
    exclude("META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/NOTICE.md")
    exclude("META-INF/DEPENDENCIES")

    // Bundle the curated attribution notice and license texts into META-INF/.
    // NOTICE covers bundled dependency attribution for the CLI distribution.
    // LICENSE is the MIT license for FinGrind's own code.
    // LICENSE-APACHE-2.0 satisfies Apache License 2.0 Section 4(a) for bundled dependencies.
    // LICENSE-SIL-OFL-1.1 satisfies the bundled Noto Sans font license terms.
    // LICENSE-SQLITE3MULTIPLECIPHERS satisfies the MIT license for the managed SQLite3MC
    // native library that ships alongside this JAR in every distribution mode.
    from(rootProject.file("NOTICE")) { into("META-INF") }
    from(rootProject.file("LICENSE")) { into("META-INF") }
    from(rootProject.file("LICENSE-APACHE-2.0")) { into("META-INF") }
    from(rootProject.file("LICENSE-SIL-OFL-1.1")) { into("META-INF") }
    from(rootProject.file("LICENSE-SQLITE3MULTIPLECIPHERS")) { into("META-INF") }

    manifest {
        attributes(
            "Implementation-Title" to "FinGrind",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to buildMetadata.implementationVendor,
            "Implementation-License" to buildMetadata.implementationLicense,
            "Automatic-Module-Name" to "dev.erst.fingrind.cli",
        )
    }
}
