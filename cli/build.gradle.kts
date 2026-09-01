import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.DuplicatesStrategy

plugins {
    application
    id("dev.erst.fingrind.java-conventions")
    id("dev.erst.fingrind.managed-sqlite-consumer")
    alias(libs.plugins.shadow)
    id("dev.erst.fingrind.cli-distribution")
}

description = "CLI transport adapter for the FinGrind execution boundary"

dependencies {
    testImplementation(libs.pdfbox)
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
val externalRuntimeLegalArtifacts =
    configurations.runtimeClasspath.get().incoming.artifactView {
        componentFilter { identifier -> identifier is ModuleComponentIdentifier }
    }.files
val stageRuntimeLegalResources =
    tasks.register<dev.erst.fingrind.buildlogic.StageRuntimeLegalResourcesTask>(
        "stageRuntimeLegalResources",
    ) {
        runtimeArtifacts.from(externalRuntimeLegalArtifacts)
        legalResourceLockFile.set(
            rootProject.layout.projectDirectory.file("gradle/runtime-legal-resources.lock.tsv"),
        )
        outputDirectory.set(layout.buildDirectory.dir("generated/runtime-legal"))
    }

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    jvmArgs("--enable-native-access=dev.erst.fingrind.core")
}

tasks.named<ShadowJar>("shadowJar") {
    dependsOn(stageRuntimeLegalResources)
    archiveBaseName = "fingrind"
    archiveVersion = ""
    archiveClassifier = ""
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    inputs.property("shadowJarImplementationTitle", "FinGrind")
    inputs.property("shadowJarImplementationVersion", project.version.toString())
    inputs.property("shadowJarImplementationVendor", buildMetadata.implementationVendor)
    inputs.property("shadowJarImplementationLicense", buildMetadata.implementationLicense)

    // Preserve every descriptor so the transformer can merge every ServiceLoader registration.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()

    // Preserve every resolved dependency's original license and notice bytes under versioned,
    // collision-free paths. PDFBox and FontBox append separately governed external-component
    // terms after their Apache text; replacing those resources with a generic license would
    // discard controlling conditions.
    exclude("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/LICENSE.md")
    exclude("META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/NOTICE.md")
    exclude("META-INF/DEPENDENCIES")

    // Bundle the distribution-level inventory and non-JAR component-specific texts into META-INF.
    from(rootProject.file("NOTICE")) { into("META-INF") }
    from(rootProject.file("NOTICE-ZULU-26.32.203")) { into("META-INF") }
    from(rootProject.file("LICENSE")) { into("META-INF") }
    from(rootProject.file("LICENSE-APACHE-2.0")) { into("META-INF") }
    from(rootProject.file("LICENSE-CC0-1.0")) { into("META-INF") }
    from(rootProject.file("LICENSE-SIL-OFL-1.1")) { into("META-INF") }
    from(rootProject.file("LICENSE-SQLITE3MULTIPLECIPHERS")) { into("META-INF") }
    from(rootProject.file("LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY")) { into("META-INF") }
    from(rootProject.file("SOURCE_OFFER.md")) { into("META-INF") }
    from(stageRuntimeLegalResources.flatMap { task -> task.outputDirectory }) {
        into("META-INF/third-party")
    }

    manifest {
        attributes(
            "Implementation-Title" to "FinGrind",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to buildMetadata.implementationVendor,
            "FinGrind-Code-License" to buildMetadata.implementationLicense,
            "Third-Party-License-Notice" to "META-INF/NOTICE",
            "Third-Party-License-Texts" to "META-INF/third-party/INDEX.tsv",
            "Automatic-Module-Name" to "dev.erst.fingrind.cli",
        )
    }
}
