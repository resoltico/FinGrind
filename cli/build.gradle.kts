import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.erst.fingrind.buildlogic.CreateTarGzArchiveTask
import dev.erst.fingrind.buildlogic.CreateRuntimeImageTask
import dev.erst.fingrind.buildlogic.WriteRuntimeModuleListTask
import dev.erst.fingrind.buildlogic.WriteSha256FileTask
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip

plugins {
    application
    id("fingrind.java-conventions")
    alias(libs.plugins.shadow)
}

description = "CLI transport adapter for the FinGrind application boundary"

dependencies {
    implementation(project(":application"))
    implementation(project(":core"))
    implementation(project(":sqlite"))
    implementation(libs.jackson.databind)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "dev.erst.fingrind.cli.App"
}

val fingrindJavaVersion = providers.gradleProperty("fingrindJavaVersion").map(String::toInt).get()
val bundleClassifier =
    providers.gradleProperty("fingrindBundleClassifier").orElse(
        providers.provider { hostBundleClassifier() },
    )
val bundleName = bundleClassifier.map { classifier -> "fingrind-${project.version}-$classifier" }
val currentJavaHomeDirectory = layout.dir(providers.provider { file(System.getProperty("java.home")) })
val shadowJarTask = tasks.named<ShadowJar>("shadowJar")
val shadowJarArchiveFile = shadowJarTask.flatMap { it.archiveFile }
val managedSqliteLibraryPath =
    rootProject.layout.buildDirectory.file(
        providers.provider {
            "managed-sqlite/${managedSqliteHostClassifier()}/${managedSqliteLibraryFileNameForHost()}"
        },
    )
val runtimeModuleListOutputFile = layout.buildDirectory.file("bundle/runtime-modules.txt")
val runtimeImageDirectory = layout.buildDirectory.dir("bundle/runtime-image")
val bundleRootDirectory = bundleName.flatMap { name -> layout.buildDirectory.dir("bundle/$name") }
val distributionDirectory = layout.buildDirectory.dir("distributions")
val bundleArchiveFileName = bundleName.map { name -> "$name.tar.gz" }
val bundleSha256File =
    bundleName.flatMap { name -> layout.buildDirectory.file("distributions/$name.tar.gz.sha256") }

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName = "fingrind"
    archiveVersion = ""
    archiveClassifier = ""

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
    val version: String = project.version.toString()
    inputs.property("fingrindDescription", description)
    inputs.property("fingrindVersion", version)
    filesMatching("fingrind.properties") {
        expand(
            mapOf(
                "fingrindDescription" to description,
                "version" to version,
            ),
        )
    }
}

tasks.named("assemble") {
    dependsOn("shadowJar")
}

tasks.named<CreateStartScripts>("startScripts") {
    enabled = false
}

tasks.named<Sync>("installDist") {
    enabled = false
}

tasks.named<Tar>("distTar") {
    enabled = false
}

tasks.named<Zip>("distZip") {
    enabled = false
}

val writeRuntimeModuleList =
    tasks.register<WriteRuntimeModuleListTask>("writeRuntimeModuleList") {
        group = "distribution"
        description = "Computes the Java module set required by the FinGrind CLI bundle."
        dependsOn(shadowJarTask)
        javaHomeDirectory.set(currentJavaHomeDirectory)
        applicationJar.set(shadowJarArchiveFile)
        javaVersion.set(fingrindJavaVersion)
        outputFile.set(runtimeModuleListOutputFile)
    }

val createRuntimeImage =
    tasks.register<CreateRuntimeImageTask>("createRuntimeImage") {
        group = "distribution"
        description = "Builds the private Java runtime image for the FinGrind CLI bundle."
        dependsOn(writeRuntimeModuleList)
        javaHomeDirectory.set(currentJavaHomeDirectory)
        runtimeModuleListFile.set(runtimeModuleListOutputFile)
        outputDirectory.set(runtimeImageDirectory)
    }

val cleanBundleRoot =
    tasks.register<Delete>("cleanBundleRoot") {
        group = "distribution"
        description = "Deletes the staged self-contained FinGrind CLI bundle directory."
        delete(bundleRootDirectory)
    }

val stageCliBundle =
    tasks.register<Sync>("stageCliBundle") {
        group = "distribution"
        description = "Stages the self-contained FinGrind CLI bundle directory."
        dependsOn(cleanBundleRoot)
        dependsOn(shadowJarTask)
        dependsOn(rootProject.tasks.named("prepareManagedSqlite"))
        dependsOn(createRuntimeImage)
        into(bundleRootDirectory)

        from(layout.projectDirectory.file("src/bundle/bin/fingrind")) {
            into("bin")
        }
        from(shadowJarArchiveFile) {
            into("lib/app")
            rename { "fingrind.jar" }
        }
        from(createRuntimeImage) {
            into("runtime")
        }
        from(managedSqliteLibraryPath) {
            into("lib/native")
        }
        from(rootProject.file("LICENSE"))
        from(rootProject.file("LICENSE-APACHE-2.0"))
        from(rootProject.file("LICENSE-SQLITE3MULTIPLECIPHERS"))
        from(rootProject.file("NOTICE"))
        from(rootProject.file("PATENTS.md"))
    }

val bundleCliTarGz =
    tasks.register<CreateTarGzArchiveTask>("bundleCliTarGz") {
        group = "distribution"
        description = "Builds the compressed self-contained FinGrind CLI bundle archive."
        dependsOn(stageCliBundle)
        sourceDirectory.set(bundleRootDirectory)
        archiveFile.set(bundleName.flatMap { name -> layout.buildDirectory.file("distributions/$name.tar.gz") })
    }

val bundleCliSha256 =
    tasks.register<WriteSha256FileTask>("bundleCliSha256") {
        group = "distribution"
        description = "Writes the SHA-256 checksum file for the FinGrind CLI bundle archive."
        dependsOn(bundleCliTarGz)
        inputFile.set(bundleCliTarGz.flatMap { it.archiveFile })
        outputFile.set(bundleSha256File)
    }

tasks.register("bundleCliArchive") {
    group = "distribution"
    description =
        "Builds the self-contained FinGrind CLI bundle archive together with its SHA-256 checksum."
    dependsOn(bundleCliTarGz)
    dependsOn(bundleCliSha256)
}

fun hostBundleClassifier(): String {
    val operatingSystemId = operatingSystemId()
    val architectureId =
        when (System.getProperty("os.arch", "").lowercase()) {
            "arm64", "aarch64" -> "aarch64"
            "amd64", "x86_64", "x64" -> "x86_64"
            else ->
                System.getProperty("os.arch", "unknown")
                    .lowercase()
                    .replace(Regex("[^a-z0-9]+"), "-")
        }
    return "$operatingSystemId-$architectureId"
}

fun managedSqliteHostClassifier(): String =
    operatingSystemId() +
        "-" +
        System.getProperty("os.arch", "unknown").lowercase().replace(Regex("[^a-z0-9]+"), "-")

fun managedSqliteLibraryFileNameForHost(): String =
    when (operatingSystemId()) {
        "macos" -> "libsqlite3.dylib"
        "linux" -> "libsqlite3.so.0"
        else -> throw IllegalStateException("Unsupported host operating system for bundle staging.")
    }

fun operatingSystemId(): String {
    val operatingSystem = System.getProperty("os.name", "").lowercase()
    if (operatingSystem.contains("mac")) {
        return "macos"
    }
    if (operatingSystem.contains("linux")) {
        return "linux"
    }
    throw IllegalStateException("FinGrind bundles currently support macOS and Linux only.")
}
