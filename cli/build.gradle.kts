import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.erst.fingrind.buildlogic.CreateRuntimeImageTask
import dev.erst.fingrind.buildlogic.DistributionContractReader
import dev.erst.fingrind.buildlogic.FinGrindBuildMetadata
import dev.erst.fingrind.buildlogic.PruneBundleOutputsTask
import dev.erst.fingrind.buildlogic.ReportBundleArchiveOutputsTask
import dev.erst.fingrind.buildlogic.WriteDockerBuildContextManifestTask
import dev.erst.fingrind.buildlogic.WriteBundleManifestTask
import dev.erst.fingrind.buildlogic.WriteRuntimeModuleListTask
import dev.erst.fingrind.buildlogic.WriteSha256FileTask
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.GradleException
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test

plugins {
    application
    id("dev.erst.fingrind.java-conventions")
    alias(libs.plugins.shadow)
}

description = "CLI transport adapter for the FinGrind execution boundary"

val repositoryRootDirectory = rootProject.projectDir.toPath()
val sourceCheckoutBuildRootDirectory = rootProject.layout.buildDirectory.get().asFile.toPath()
val sourceCheckoutRuntimeDistribution =
    DistributionContractReader.sourceCheckoutRuntimeDistribution(repositoryRootDirectory)

dependencies {
    implementation(project(":contract"))
    implementation(project(":core"))
    implementation(project(":executor"))
    implementation(project(":report-pdf"))
    implementation(project(":sqlite"))
    implementation(libs.jackson.databind)
}

application {
    mainClass = "dev.erst.fingrind.cli.App"
    applicationDefaultJvmArgs =
        listOf(
            "--enable-native-access=ALL-UNNAMED",
            "-Dfingrind.runtime.distribution=${sourceCheckoutRuntimeDistribution}",
            "-Dfingrind.source-checkout.root=${repositoryRootDirectory}",
            "-Dfingrind.source-checkout.build-root=${sourceCheckoutBuildRootDirectory}",
        )
}

val buildMetadata = FinGrindBuildMetadata.load(project)
val fingrindJavaVersion = buildMetadata.javaVersion
val publicCliBundleTargets = DistributionContractReader.publicCliBundleTargets(repositoryRootDirectory)
val unsupportedPublicCliBundleTargets =
    DistributionContractReader.unsupportedPublicCliBundleTargets(repositoryRootDirectory)
val hostBundleTarget = DistributionContractReader.hostBundleTarget(repositoryRootDirectory)
val containerRuntimeDistribution =
    DistributionContractReader.containerRuntimeDistribution(repositoryRootDirectory)
val bundleRuntimeDistribution =
    DistributionContractReader.bundleRuntimeDistribution(repositoryRootDirectory)
val publicCliDistribution =
    DistributionContractReader.publicCliDistribution(repositoryRootDirectory)
val storageDriver = DistributionContractReader.storageDriver(repositoryRootDirectory)
val storageEngine = DistributionContractReader.storageEngine(repositoryRootDirectory)
val bookProtectionMode = DistributionContractReader.bookProtectionMode(repositoryRootDirectory)
val defaultBookCipher = DistributionContractReader.defaultBookCipher(repositoryRootDirectory)
val sqliteLibraryMode = DistributionContractReader.sqliteLibraryMode(repositoryRootDirectory)
val sqliteBundleHomeSystemProperty =
    DistributionContractReader.sqliteBundleHomeSystemProperty(repositoryRootDirectory)
val bundleClassifier =
    providers.gradleProperty("fingrindBundleClassifier").orElse(
        providers.provider { hostBundleTarget.classifier },
    )
val bundleName = bundleClassifier.map { classifier -> "fingrind-${project.version}-$classifier" }
val currentJavaHomeDirectory = layout.dir(providers.provider { file(System.getProperty("java.home")) })
val compileOnlyConfiguration = configurations.named("compileOnly")
val jdepsInputsConfiguration =
    configurations.create("jdepsInputs") {
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
val managedSqliteLibrarySha256Path =
    rootProject.layout.buildDirectory.file(
        providers.provider {
            "managed-sqlite/${managedSqliteHostClassifier()}/${managedSqliteLibraryFileNameForHost()}.sha256"
        },
    )
val dockerBuildContextDirectory = layout.buildDirectory.dir("docker-context")
val dockerBuildContextManifestOutputFile =
    layout.buildDirectory.file("generated/docker/docker-build-context-manifest.json")
val runtimeModuleListOutputFile = layout.buildDirectory.file("bundle/runtime-modules.txt")
val runtimeImageDirectory = layout.buildDirectory.dir("bundle/runtime-image")
val bundleWorkspaceDirectory = layout.buildDirectory.dir("bundle")
val bundleRootDirectory = bundleName.flatMap { name -> layout.buildDirectory.dir("bundle/$name") }
val bundleManifestOutputFile =
    layout.buildDirectory.file("generated/bundle/root/bundle-manifest.json")
val distributionDirectory = layout.buildDirectory.dir("distributions")
val dockerBuildContextFiles =
    listOf(
        "docker-build-context-manifest.json",
        "docker-entrypoint.sh",
        "fingrind.jar",
        "managed-sqlite-contract.json",
        "runtime-modules.txt",
    )
val dockerManagedSqliteContractSource =
    providers.provider {
        DistributionContractReader.requiredContractFiles(repositoryRootDirectory).single {
            it.fileName.toString() == "managed-sqlite-contract.json"
        }
    }
val bundleClassifierValue = bundleClassifier.get()
val bundleTarget = DistributionContractReader.bundleTarget(repositoryRootDirectory, bundleClassifierValue)
val bundleOperatingSystemId = bundleTarget.operatingSystemId
val bundleArchitectureId = bundleTarget.architectureId
val bundleArchiveExtension = providers.provider { bundleTarget.archiveFormat }
val bundleArchiveFileName = bundleName.zip(bundleArchiveExtension) { name, extension -> "$name.$extension" }
val bundleSha256File =
    bundleArchiveFileName.flatMap { fileName -> layout.buildDirectory.file("distributions/$fileName.sha256") }
val hostBundleClassifier = hostBundleTarget.classifier
val bundleLauncherPath = providers.provider { bundleTarget.launcherPath }
val bundleLauncherCommand = providers.provider { bundleTarget.launcherCommand }
val bundleTemplateProperties =
    mapOf(
        "version" to project.version.toString(),
        "bundleArchiveFormat" to bundleArchiveExtension.get(),
        "bundleClassifier" to bundleClassifierValue,
        "bundleOperatingSystem" to bundleOperatingSystemId,
        "bundleArchitecture" to bundleArchitectureId,
        "bundleLauncherPath" to bundleLauncherPath.get(),
        "bundleLauncherCommand" to bundleLauncherCommand.get(),
        "bundleRuntimeDistribution" to bundleRuntimeDistribution,
        "publicCliDistribution" to publicCliDistribution,
        "storageDriver" to storageDriver,
        "storageEngine" to storageEngine,
        "bookProtectionMode" to bookProtectionMode,
        "defaultBookCipher" to defaultBookCipher,
        "sqliteLibraryMode" to sqliteLibraryMode,
        "sqliteBundleHomeSystemProperty" to sqliteBundleHomeSystemProperty,
        "requiredMinimumSqliteVersion" to
            DistributionContractReader.requiredMinimumSqliteVersion(repositoryRootDirectory),
        "requiredSqlite3mcVersion" to
            DistributionContractReader.requiredSqlite3mcVersion(repositoryRootDirectory),
        "helpOperation" to DistributionContractReader.helpOperationName(repositoryRootDirectory),
        "capabilitiesOperation" to
            DistributionContractReader.capabilitiesOperationName(repositoryRootDirectory),
        "requestTemplateOperation" to
            DistributionContractReader.requestTemplateOperationName(repositoryRootDirectory),
        "planTemplateOperation" to
            DistributionContractReader.planTemplateOperationName(repositoryRootDirectory),
        "publicBundleTargetsMarkdown" to
            DistributionContractReader.markdownBulletList(publicCliBundleTargets),
        "unsupportedPublicBundleTargetsMarkdown" to
            DistributionContractReader.markdownBulletList(unsupportedPublicCliBundleTargets),
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
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName = "fingrind"
    archiveVersion = ""
    archiveClassifier = ""
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
            "Enable-Native-Access" to "ALL-UNNAMED",
            "FinGrind-Source-Checkout-Root" to repositoryRootDirectory.toString(),
            "FinGrind-Source-Checkout-Build-Root" to sourceCheckoutBuildRootDirectory.toString(),
        )
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(rootProject.tasks.named("prepareManagedSqlite"))
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
        additionalModules.set(listOf("jdk.unsupported"))
        dependencyClasspath.from(jdepsInputsConfiguration)
        outputFile.set(runtimeModuleListOutputFile)
    }

val writeDockerBuildContextManifest =
    tasks.register<WriteDockerBuildContextManifestTask>("writeDockerBuildContextManifest") {
        group = "distribution"
        description = "Writes the manifest for the staged Docker build context."
        ownerTaskName.set("stageDockerBuildContext")
        fileNames.set(dockerBuildContextFiles)
        outputFile.set(dockerBuildContextManifestOutputFile)
    }

val stageDockerBuildContext =
    tasks.register<Sync>("stageDockerBuildContext") {
        group = "distribution"
        description = "Stages the complete Docker build context consumed by the container image build."
        dependsOn(shadowJarTask)
        dependsOn(writeRuntimeModuleList)
        dependsOn(writeDockerBuildContextManifest)
        into(dockerBuildContextDirectory)

        from(writeDockerBuildContextManifest) {
            rename { "docker-build-context-manifest.json" }
        }
        from(shadowJarArchiveFile) {
            rename { "fingrind.jar" }
        }
        from(runtimeModuleListOutputFile) {
            rename { "runtime-modules.txt" }
        }
        from(layout.projectDirectory.dir("src/docker")) {
            include("docker-entrypoint.sh")
            filter<ReplaceTokens>(
                "tokens" to
                    mapOf(
                        "containerRuntimeDistribution" to containerRuntimeDistribution,
                    ),
                "beginToken" to "{{",
                "endToken" to "}}",
            )
        }
        from(dockerManagedSqliteContractSource) {
            rename { "managed-sqlite-contract.json" }
        }
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

val writeBundleManifest =
    tasks.register<WriteBundleManifestTask>("writeBundleManifest") {
        group = "distribution"
        description = "Writes the generated bundle manifest for the self-contained CLI archive."
        contractFiles.from(DistributionContractReader.requiredContractFiles(repositoryRootDirectory))
        projectRootDirectoryPath.set(repositoryRootDirectory.toString())
        applicationName.set(rootProject.name)
        versionText.set(project.version.toString())
        bundleClassifier.set(bundleClassifierValue)
        outputFile.set(bundleManifestOutputFile)
    }

val cleanBundleOutputs =
    tasks.register<PruneBundleOutputsTask>("cleanBundleOutputs") {
        group = "distribution"
        description =
            "Deletes staged self-contained FinGrind CLI bundle directories plus prior bundle archives and checksum files."
        artifactPrefix.set("fingrind-")
        bundleWorkspaceDirectory.set(layout.buildDirectory.dir("bundle"))
        bundleRootDirectory.set(layout.buildDirectory.dir(bundleName.map { name -> "bundle/$name" }))
        distributionDirectory.set(layout.buildDirectory.dir("distributions"))
        legacyBundleWorkspaceDirectory.set(layout.projectDirectory.dir("build/bundle"))
        legacyDistributionDirectory.set(layout.projectDirectory.dir("build/distributions"))
    }

val stageCliBundle =
    tasks.register<Sync>("stageCliBundle") {
        group = "distribution"
        description = "Stages the self-contained FinGrind CLI bundle directory."
        dependsOn(cleanBundleOutputs)
        dependsOn(shadowJarTask)
        dependsOn(rootProject.tasks.named("prepareManagedSqlite"))
        dependsOn(createRuntimeImage)
        dependsOn(writeBundleManifest)
        into(bundleRootDirectory)
        inputs.properties(bundleTemplateProperties)

        from(layout.projectDirectory.dir("src/bundle/bin")) {
            into("bin")
            filter<ReplaceTokens>(
                "tokens" to
                    mapOf(
                        "bundleRuntimeDistribution" to bundleRuntimeDistribution,
                        "bundleHomeSystemProperty" to sqliteBundleHomeSystemProperty,
                    ),
                "beginToken" to "{{",
                "endToken" to "}}",
            )
        }
        from(layout.projectDirectory.dir("src/bundle/root")) {
            expand(bundleTemplateProperties)
        }
        from(bundleManifestOutputFile)
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
        from(managedSqliteLibrarySha256Path) {
            into("lib/native")
        }
        from(rootProject.file("LICENSE"))
        from(rootProject.file("LICENSE-APACHE-2.0"))
        from(rootProject.file("LICENSE-SIL-OFL-1.1"))
        from(rootProject.file("LICENSE-SQLITE3MULTIPLECIPHERS"))
        from(rootProject.file("NOTICE"))
        from(rootProject.file("PATENTS.md"))
    }

val bundleArchiveTask: org.gradle.api.tasks.TaskProvider<out AbstractArchiveTask> =
    if (bundleOperatingSystemId == "windows") {
        tasks.register<Zip>("bundleCliZip") {
            group = "distribution"
            description = "Builds the compressed self-contained FinGrind CLI bundle archive."
            dependsOn(stageCliBundle)
            destinationDirectory.set(distributionDirectory)
            archiveFileName.set(bundleArchiveFileName)
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

tasks.register<ReportBundleArchiveOutputsTask>("bundleCliArchive") {
    group = "distribution"
    description =
        "Builds the self-contained FinGrind CLI bundle archive together with its SHA-256 checksum."
    dependsOn(bundleArchiveTask)
    dependsOn(bundleCliSha256)
    archiveFile.set(bundleArchiveTask.flatMap { it.archiveFile })
    checksumFile.set(bundleCliSha256.flatMap { it.outputFile })
}

tasks.named<Test>("test") {
    jvmArgs(
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-exports=java.base/jdk.internal.io=ALL-UNNAMED",
    )
    inputs.file(rootProject.layout.projectDirectory.file("Dockerfile"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file(".gitignore"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file(".gitattributes"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("AGENTS.md"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file(".codex/UNIVERSAL_ENGINEERING_CONTRACT.md"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("build.gradle.kts"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("docs"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("scripts"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("gradle/build-logic"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

fun hostBundleClassifier(): String {
    return DistributionContractReader.hostBundleTarget(repositoryRootDirectory).classifier
}

fun managedSqliteHostClassifier(): String =
    DistributionContractReader.hostBundleTarget(repositoryRootDirectory).classifier

fun managedSqliteLibraryFileNameForHost(): String =
    DistributionContractReader.hostBundleTarget(repositoryRootDirectory).sqliteLibraryFileName

fun operatingSystemId(): String {
    return DistributionContractReader.hostBundleTarget(repositoryRootDirectory).operatingSystemId
}
