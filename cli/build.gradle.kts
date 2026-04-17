import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.erst.fingrind.buildlogic.CreateRuntimeImageTask
import dev.erst.fingrind.buildlogic.DistributionSupport
import dev.erst.fingrind.buildlogic.WriteRuntimeModuleListTask
import dev.erst.fingrind.buildlogic.WriteSha256FileTask
import org.gradle.api.GradleException
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip

plugins {
    application
    id("fingrind.java-conventions")
    alias(libs.plugins.shadow)
}

description = "CLI transport adapter for the FinGrind execution boundary"

dependencies {
    implementation(project(":contract"))
    implementation(project(":core"))
    implementation(project(":executor"))
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
        providers.provider { DistributionSupport.hostClassifier() },
    )
val bundleName = bundleClassifier.map { classifier -> "fingrind-${project.version}-$classifier" }
val currentJavaHomeDirectory = layout.dir(providers.provider { file(System.getProperty("java.home")) })
val compileOnlyConfiguration = configurations.named("compileOnly")
val jdepsSupportConfiguration =
    configurations.create("jdepsSupport") {
        isCanBeConsumed = false
        isCanBeResolved = true
        extendsFrom(compileOnlyConfiguration.get())
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        }
    }
val shadowJarTask = tasks.named<ShadowJar>("shadowJar")
val shadowJarArchiveFile = shadowJarTask.flatMap { it.archiveFile }
val managedSqliteLibraryPath =
    rootProject.layout.buildDirectory.file(
        providers.provider {
            "managed-sqlite/${managedSqliteHostClassifier()}/${managedSqliteLibraryFileNameForHost()}"
        },
    )
val dockerJdepsSupportDirectory = layout.buildDirectory.dir("docker/jdeps")
val runtimeModuleListOutputFile = layout.buildDirectory.file("bundle/runtime-modules.txt")
val runtimeImageDirectory = layout.buildDirectory.dir("bundle/runtime-image")
val bundleRootDirectory = bundleName.flatMap { name -> layout.buildDirectory.dir("bundle/$name") }
val distributionDirectory = layout.buildDirectory.dir("distributions")
val bundleClassifierValue = bundleClassifier.get()
val bundleOperatingSystem = bundleClassifierValue.substringBefore('-')
val bundleArchitecture = bundleClassifierValue.substringAfter('-')
val bundleArchiveExtension =
    providers.provider { DistributionSupport.archiveExtensionForOperatingSystemId(bundleOperatingSystem) }
val bundleArchiveFileName = bundleName.zip(bundleArchiveExtension) { name, extension -> "$name.$extension" }
val bundleSha256File =
    bundleArchiveFileName.flatMap { fileName -> layout.buildDirectory.file("distributions/$fileName.sha256") }
val hostBundleClassifier = DistributionSupport.hostClassifier()
val bundleLauncherPath =
    providers.provider { DistributionSupport.launcherPathForOperatingSystemId(bundleOperatingSystem) }
val bundleLauncherCommand =
    providers.provider { DistributionSupport.launcherCommandForOperatingSystemId(bundleOperatingSystem) }
val bundleTemplateProperties =
    mapOf(
        "version" to project.version.toString(),
        "bundleClassifier" to bundleClassifierValue,
        "bundleArchiveFormat" to bundleArchiveExtension.get(),
        "bundleOperatingSystem" to bundleOperatingSystem,
        "bundleArchitecture" to bundleArchitecture,
        "bundleLauncherPath" to bundleLauncherPath.get(),
        "bundleLauncherCommand" to bundleLauncherCommand.get(),
        "bundleLauncherCommandJson" to DistributionSupport.jsonString(bundleLauncherCommand.get()),
        "publicBundleTargetsJson" to
            DistributionSupport.jsonStringArray(DistributionSupport.PUBLIC_CLI_BUNDLE_TARGETS),
        "unsupportedPublicOperatingSystemsJson" to
            DistributionSupport.jsonStringArray(
                DistributionSupport.UNSUPPORTED_PUBLIC_CLI_OPERATING_SYSTEMS,
            ),
        "publicBundleTargetsMarkdown" to
            DistributionSupport.markdownBulletList(DistributionSupport.PUBLIC_CLI_BUNDLE_TARGETS),
        "unsupportedPublicOperatingSystemsMarkdown" to
            DistributionSupport.markdownBulletList(
                DistributionSupport.UNSUPPORTED_PUBLIC_CLI_OPERATING_SYSTEMS,
            ),
    )

if (bundleClassifierValue != hostBundleClassifier) {
    throw GradleException(
        "FinGrind bundle builds are host-native only. Requested classifier $bundleClassifierValue " +
            "but the current host can only build $hostBundleClassifier because the private runtime " +
            "image and managed SQLite library are produced for the active host platform.",
    )
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    jvmArgs("-Dfingrind.runtime.distribution=source-checkout-gradle")
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

val stageDockerJdepsSupport =
    tasks.register<Sync>("stageDockerJdepsSupport") {
        group = "distribution"
        description =
            "Stages compile-only dependency jars required to analyze the Docker assembly input."
        from(jdepsSupportConfiguration)
        into(dockerJdepsSupportDirectory)
    }

tasks.named<ShadowJar>("shadowJar") {
    finalizedBy(stageDockerJdepsSupport)
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
        dependencyClasspath.from(jdepsSupportConfiguration)
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
        inputs.properties(bundleTemplateProperties)

        from(layout.projectDirectory.dir("src/bundle/bin")) {
            into("bin")
        }
        from(layout.projectDirectory.dir("src/bundle/root")) {
            expand(bundleTemplateProperties)
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

val bundleArchiveTask: org.gradle.api.tasks.TaskProvider<out AbstractArchiveTask> =
    if (bundleOperatingSystem == "windows") {
        tasks.register<Zip>("bundleCliZip") {
            group = "distribution"
            description = "Builds the compressed self-contained FinGrind CLI bundle archive."
            dependsOn(stageCliBundle)
            destinationDirectory.set(distributionDirectory)
            archiveFileName.set(bundleArchiveFileName)
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
            dirPermissions {
                unix(493)
            }
            filePermissions {
                unix(420)
            }
            from(bundleRootDirectory) {
                into(bundleName)
                eachFile {
                    if (file.canExecute()) {
                        permissions {
                            unix(493)
                        }
                    }
                }
            }
        }
    } else {
        tasks.register<Tar>("bundleCliTarGz") {
            group = "distribution"
            description = "Builds the compressed self-contained FinGrind CLI bundle archive."
            dependsOn(stageCliBundle)
            destinationDirectory.set(distributionDirectory)
            archiveFileName.set(bundleArchiveFileName)
            compression = Compression.GZIP
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
            dirPermissions {
                unix(493)
            }
            filePermissions {
                unix(420)
            }
            from(bundleRootDirectory) {
                into(bundleName)
                eachFile {
                    if (file.canExecute()) {
                        permissions {
                            unix(493)
                        }
                    }
                }
            }
        }
    }

val bundleCliSha256 =
    tasks.register<WriteSha256FileTask>("bundleCliSha256") {
        group = "distribution"
        description = "Writes the SHA-256 checksum file for the FinGrind CLI bundle archive."
        dependsOn(bundleArchiveTask)
        inputFile.set(bundleArchiveTask.flatMap { it.archiveFile })
        outputFile.set(bundleSha256File)
    }

tasks.register("bundleCliArchive") {
    group = "distribution"
    description =
        "Builds the self-contained FinGrind CLI bundle archive together with its SHA-256 checksum."
    dependsOn(bundleArchiveTask)
    dependsOn(bundleCliSha256)
}

fun hostBundleClassifier(): String {
    return DistributionSupport.hostClassifier()
}

fun managedSqliteHostClassifier(): String =
    DistributionSupport.hostClassifier()

fun managedSqliteLibraryFileNameForHost(): String =
    DistributionSupport.libraryFileNameForOperatingSystemId(operatingSystemId())

fun operatingSystemId(): String {
    return DistributionSupport.operatingSystemId()
}
