package dev.erst.fingrind.buildlogic

import java.time.Instant
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

class FinGrindCliDistributionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            val repositoryRootDirectory = rootProject.projectDir.toPath()
            val fingrindJavaVersion = FinGrindBuildMetadata.load(this).javaVersion
            val javaToolchainService = project.extensions.getByType<JavaToolchainService>()
            val sourceCheckoutJavaLauncher =
                javaToolchainService.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(fingrindJavaVersion))
                }
            val publicCliBundleTargets =
                DistributionContractReader.publicCliBundleTargets(repositoryRootDirectory)
            val unsupportedPublicCliBundleTargets =
                DistributionContractReader.unsupportedPublicCliBundleTargets(repositoryRootDirectory)
            val hostBundleTarget = DistributionBundleTargetReader.hostBundleTarget(repositoryRootDirectory)
            val hostManagedSqlite = ManagedSqliteProvisioningRegistry.require(rootProject)
            val managedSqlitePackageId =
                DistributionContractReader.requiredSqliteSourcePackageId(repositoryRootDirectory)
            val dockerManagedSqlite =
                ManagedSqliteProvisioningLogic.registerDockerContextTarget(
                    project = rootProject,
                    hostProvisioning = hostManagedSqlite,
                    repositoryRootDirectory = repositoryRootDirectory,
                    sqliteSourceDirectory =
                        rootProject.layout.projectDirectory.dir(
                            "third_party/sqlite/$managedSqlitePackageId",
                        ),
                    sqliteVersionValue =
                        DistributionContractReader.requiredMinimumSqliteVersion(repositoryRootDirectory),
                    sqlite3mcVersionValue =
                        DistributionContractReader.requiredSqlite3mcVersion(repositoryRootDirectory),
                    sourcePackageId = managedSqlitePackageId,
                )
            val containerRuntimeDistribution =
                DistributionContractReader.containerRuntimeDistribution(repositoryRootDirectory)
            val bundleRuntimeDistribution =
                DistributionContractReader.bundleRuntimeDistribution(repositoryRootDirectory)
            val publicCliDistribution =
                DistributionContractReader.publicCliDistribution(repositoryRootDirectory)
            val storageDriver = DistributionContractReader.storageDriver(repositoryRootDirectory)
            val storageEngine = DistributionContractReader.storageEngine(repositoryRootDirectory)
            val bookProtectionMode =
                DistributionContractReader.bookProtectionMode(repositoryRootDirectory)
            val defaultBookCipher = DistributionContractReader.defaultBookCipher(repositoryRootDirectory)
            val sqliteLibraryMode =
                DistributionContractReader.sqliteLibraryMode(repositoryRootDirectory)
            val sqliteBundleHomeSystemProperty =
                DistributionContractReader.sqliteBundleHomeSystemProperty(repositoryRootDirectory)
            val allowedRuntimeModuleMissingDependencyPrefixes =
                DistributionContractReader.allowedRuntimeModuleMissingDependencyPrefixes(
                    repositoryRootDirectory,
                )
            val bundleClassifier =
                providers.gradleProperty("fingrindBundleClassifier").orElse(
                    providers.provider { hostBundleTarget.classifier },
                )
            val bundleName =
                bundleClassifier.map { classifier -> "fingrind-${project.version}-$classifier" }
            val compileOnlyConfiguration = configurations.named("compileOnly")
            val jdepsInputsConfiguration =
                configurations.create("jdepsInputs") {
                    isCanBeConsumed = false
                    isCanBeResolved = true
                    extendsFrom(compileOnlyConfiguration.get())
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                        attribute(
                            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                            objects.named(LibraryElements.JAR),
                        )
                    }
                }
            val shadowJarTask = tasks.named<Jar>("shadowJar")
            val shadowJarArchiveFile = shadowJarTask.flatMap { it.archiveFile }
            val dockerBuildContextDirectory = layout.buildDirectory.dir("docker-context")
            val dockerBuildContextManifestOutputFile =
                layout.buildDirectory.file("generated/docker/docker-build-context-manifest.json")
            val sourceCheckoutRuntimeManifestOutputFile =
                layout.buildDirectory.file(
                    "generated/source-checkout/source-checkout-runtime-manifest.tsv",
                )
            val dockerBuildContextSourceInputs =
                CliDistributionSourceInventory.dockerBuildContextSourceFiles(
                    project,
                    repositoryRootDirectory,
                )
            val cliContractBuildLogicInputs =
                objects.fileCollection().from(
                    rootProject.layout.projectDirectory.file("gradle/build-logic/build.gradle.kts"),
                    rootProject.layout.projectDirectory.file("gradle/build-logic/settings.gradle.kts"),
                    rootProject.layout.projectDirectory.dir("gradle/build-logic/src/main"),
                )
            val sourceCheckoutRuntimeInputInventory =
                listOf(
                    rootProject.layout.projectDirectory.dir("cli/src/main").asFile,
                    rootProject.layout.projectDirectory.dir("contract/src/main").asFile,
                    rootProject.layout.projectDirectory.dir("core/src/main").asFile,
                    rootProject.layout.projectDirectory.dir("executor/src/main").asFile,
                    rootProject.layout.projectDirectory.dir("report-pdf/src/main").asFile,
                    rootProject.layout.projectDirectory.dir("sqlite/src/main").asFile,
                    rootProject.layout.projectDirectory.dir("gradle/build-logic/src/main").asFile,
                    rootProject.layout.projectDirectory.dir("third_party/sqlite/$managedSqlitePackageId").asFile,
                    rootProject.layout.projectDirectory.file("build.gradle.kts").asFile,
                    rootProject.layout.projectDirectory.file("settings.gradle.kts").asFile,
                    rootProject.layout.projectDirectory.file("gradle.properties").asFile,
                    rootProject.layout.projectDirectory.file("gradle/libs.versions.toml").asFile,
                    rootProject.layout.projectDirectory.file("gradle/fingrind-build.properties").asFile,
                    rootProject.layout.projectDirectory.file("gradle/build-logic/build.gradle.kts").asFile,
                    rootProject.layout.projectDirectory.file("gradle/build-logic/settings.gradle.kts").asFile,
                    rootProject.layout.projectDirectory.file("LICENSE").asFile,
                    rootProject.layout.projectDirectory.file("LICENSE-APACHE-2.0").asFile,
                    rootProject.layout.projectDirectory.file("LICENSE-SIL-OFL-1.1").asFile,
                    rootProject.layout.projectDirectory.file("LICENSE-SQLITE3MULTIPLECIPHERS").asFile,
                    rootProject.layout.projectDirectory.file("NOTICE").asFile,
                )
            val sourceCheckoutRuntimeInputs =
                objects.fileCollection().from(sourceCheckoutRuntimeInputInventory)
            val runtimeModuleListOutputFile = layout.buildDirectory.file("bundle/runtime-modules.txt")
            val runtimeImageDirectory = layout.buildDirectory.dir("bundle/runtime-image")
            val bundleWorkspaceDirectory = layout.buildDirectory.dir("bundle")
            val bundleRootDirectory =
                bundleName.flatMap { name -> layout.buildDirectory.dir("bundle/$name") }
            val bundleManifestOutputFile =
                layout.buildDirectory.file("generated/bundle/root/bundle-manifest.json")
            val bundleArchiveManifestOutputFile =
                layout.buildDirectory.file("generated/bundle/bundle-archive-manifest.json")
            val distributionDirectory = layout.buildDirectory.dir("distributions")
            val dockerBuildContextFiles = CliDistributionSourceInventory.dockerBuildContextFiles()
            val dockerManagedSqliteContractSource =
                providers.provider {
                    DistributionContractReader.requiredContractFiles(repositoryRootDirectory).single {
                        it.fileName.toString() == "managed-sqlite-contract.json"
                    }
                }
            val bundleClassifierValue = bundleClassifier.get()
            val bundleTarget =
                DistributionBundleTargetReader.bundleTarget(
                    repositoryRootDirectory,
                    bundleClassifierValue,
                )
            val bundleOperatingSystemId = bundleTarget.operatingSystemId
            val bundleArchitectureId = bundleTarget.architectureId
            val bundleArchiveExtension = providers.provider { bundleTarget.archiveFormat }
            val bundleArchiveFileName =
                bundleName.zip(bundleArchiveExtension) { name, extension -> "$name.$extension" }
            val bundleSha256File =
                bundleArchiveFileName.flatMap { fileName ->
                    layout.buildDirectory.file("distributions/$fileName.sha256")
                }
            val hostBundleClassifier = hostBundleTarget.classifier
            val bundleLauncherPath = providers.provider { bundleTarget.launcherPath }
            val bundleLauncherCommand = providers.provider { bundleTarget.launcherCommand }
            val normalizedArtifactTimestamp =
                providers.provider {
                    NormalizedArtifactTimestampResolver.resolve(repositoryRootDirectory)
                }
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
                    "helpOperation" to
                        DistributionContractReader.helpOperationName(repositoryRootDirectory),
                    "capabilitiesOperation" to
                        DistributionContractReader.capabilitiesOperationName(repositoryRootDirectory),
                    "requestTemplateOperation" to
                        DistributionContractReader.requestTemplateOperationName(repositoryRootDirectory),
                    "planTemplateOperation" to
                        DistributionContractReader.planTemplateOperationName(repositoryRootDirectory),
                    "publicBundleTargetsMarkdown" to
                        DistributionTextRendering.markdownBulletList(publicCliBundleTargets),
                    "unsupportedPublicBundleTargetsMarkdown" to
                        DistributionTextRendering.markdownBulletList(unsupportedPublicCliBundleTargets),
                )

            if (bundleClassifierValue != hostBundleClassifier) {
                throw GradleException(
                    "FinGrind bundle builds are host-native only. Requested classifier $bundleClassifierValue " +
                        "but the current host can only build $hostBundleClassifier because the private runtime " +
                        "image and managed SQLite library are produced for the active host platform.",
                )
            }

            configureCliExecutionSurfaceConventions(cliContractBuildLogicInputs)

            val writeSourceCheckoutRuntimeManifest =
                tasks.register<WriteSourceCheckoutRuntimeManifestTask>(
                    "writeSourceCheckoutRuntimeManifest",
                ) {
                    group = "distribution"
                    description =
                        "Writes the source-checkout runtime manifest that points supported developer launchers at the Gradle-owned Java 26 toolchain."
                    dependsOn(shadowJarTask)
                    ownerTaskName.set("writeSourceCheckoutRuntimeManifest")
                    javaExecutable.set(sourceCheckoutJavaLauncher.map { it.executablePath })
                    javaInstallationDirectory.set(
                        sourceCheckoutJavaLauncher.map { it.metadata.installationPath },
                    )
                    nativeAccessModule.set("dev.erst.fingrind.cli")
                    applicationModule.set("dev.erst.fingrind.cli/dev.erst.fingrind.cli.App")
                    runtimeInputs.from(sourceCheckoutRuntimeInputs)
                    runtimeInputPaths.set(
                        sourceCheckoutRuntimeInputInventory.map { runtimeInput ->
                            runtimeInput.toPath().toAbsolutePath().normalize().toString()
                        },
                    )
                    outputFile.set(sourceCheckoutRuntimeManifestOutputFile)
                }

            tasks.register("prepareSourceCheckoutCliRuntime") {
                group = "distribution"
                description =
                    "Builds the prepared source-checkout CLI runtime consumed by the developer launcher wrappers."
                dependsOn(shadowJarTask)
                dependsOn(rootProject.tasks.named("prepareManagedSqlite"))
                dependsOn(writeSourceCheckoutRuntimeManifest)
            }

            shadowJarTask.configure {
                // The CLI is not a servlet container; these optional Commons Logging hooks require APIs it does not ship.
                exclude("org/apache/commons/logging/impl/ServletContextCleaner.class")
                exclude("org/apache/commons/logging/jakarta/ServletContextCleaner.class")
                finalizedBy(writeSourceCheckoutRuntimeManifest)
            }

            val writeRuntimeModuleList =
                tasks.register<WriteRuntimeModuleListTask>("writeRuntimeModuleList") {
                    group = "distribution"
                    description = "Computes the Java module set required by the FinGrind CLI bundle."
                    dependsOn(shadowJarTask)
                    javaExecutable.set(sourceCheckoutJavaLauncher.map { it.executablePath })
                    javaInstallationDirectory.set(
                        sourceCheckoutJavaLauncher.map { it.metadata.installationPath },
                    )
                    applicationJar.set(shadowJarArchiveFile)
                    javaVersion.set(fingrindJavaVersion)
                    additionalModules.set(listOf("jdk.unsupported"))
                    allowedMissingDependencyPrefixes.set(
                        allowedRuntimeModuleMissingDependencyPrefixes,
                    )
                    dependencyClasspath.from(jdepsInputsConfiguration)
                    outputFile.set(runtimeModuleListOutputFile)
                }

            val stageDockerBuildContext =
                registerCliDockerBuildContextTasks(
                    shadowJarTask = shadowJarTask,
                    writeRuntimeModuleList = writeRuntimeModuleList,
                    dockerBuildContextDirectory = dockerBuildContextDirectory,
                    dockerBuildContextManifestOutputFile = dockerBuildContextManifestOutputFile,
                    dockerBuildContextFiles = dockerBuildContextFiles,
                    dockerBuildContextSourceInputs = dockerBuildContextSourceInputs,
                    dockerManagedSqliteContractSource = dockerManagedSqliteContractSource,
                    managedSqliteProvisioning = dockerManagedSqlite,
                    sqliteBundleHomeSystemProperty = sqliteBundleHomeSystemProperty,
                    containerRuntimeDistribution = containerRuntimeDistribution,
                )

            val createRuntimeImage =
                tasks.register<CreateRuntimeImageTask>("createRuntimeImage") {
                    group = "distribution"
                    description = "Builds the private Java runtime image for the FinGrind CLI bundle."
                    outputs.doNotCacheIf("jlink output trees carry host-private filesystem metadata.") {
                        true
                    }
                    dependsOn(writeRuntimeModuleList)
                    javaExecutable.set(sourceCheckoutJavaLauncher.map { it.executablePath })
                    javaInstallationDirectory.set(
                        sourceCheckoutJavaLauncher.map { it.metadata.installationPath },
                    )
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
                    this.bundleClassifier.set(bundleClassifierValue)
                    normalizedArtifactTimestampUtc.set(normalizedArtifactTimestamp.map(Instant::toString))
                    outputFile.set(bundleManifestOutputFile)
                }

            val cleanBundleOutputs =
                tasks.register<PruneBundleOutputsTask>("cleanBundleOutputs") {
                    group = "distribution"
                    description =
                        "Deletes staged self-contained FinGrind CLI bundle directories plus prior bundle archives and checksum files."
                    artifactPrefix.set("fingrind-")
                    this.bundleWorkspaceDirectory.set(bundleWorkspaceDirectory)
                    this.bundleRootDirectory.set(
                        layout.buildDirectory.dir(bundleName.map { name -> "bundle/$name" }),
                    )
                    this.distributionDirectory.set(layout.buildDirectory.dir("distributions"))
                    legacyBundleWorkspaceDirectory.set(layout.projectDirectory.dir("build/bundle"))
                    legacyDistributionDirectory.set(layout.projectDirectory.dir("build/distributions"))
                }

            val stageCliBundle =
                tasks.register<Sync>("stageCliBundle") {
                    group = "distribution"
                    description = "Stages the self-contained FinGrind CLI bundle directory."
                    dependsOn(cleanBundleOutputs)
                    dependsOn(shadowJarTask)
                    dependsOn(hostManagedSqlite.prepareTask)
                    dependsOn(createRuntimeImage)
                    dependsOn(writeBundleManifest)
                    into(bundleRootDirectory)
                    inputs.properties(bundleTemplateProperties)

                    from(layout.projectDirectory.dir("src/bundle/bin")) {
                        into("bin")
                        filter(
                            mapOf(
                                "tokens" to bundleTemplateProperties,
                                "beginToken" to "{{",
                                "endToken" to "}}",
                            ),
                            ReplaceTokens::class.java,
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
                    from(runtimeImageDirectory) {
                        into("runtime")
                    }
                    from(hostManagedSqlite.libraryPath) {
                        into("lib/native")
                    }
                    from(hostManagedSqlite.checksumPath) {
                        into("lib/native")
                    }
                    from(hostManagedSqlite.toolchainFingerprintPath) {
                        into("lib/native")
                    }
                    from(hostManagedSqlite.buildContractPath) {
                        into("lib/native")
                    }
                    from(rootProject.file("LICENSE"))
                    from(rootProject.file("LICENSE-APACHE-2.0"))
                    from(rootProject.file("LICENSE-SIL-OFL-1.1"))
                    from(rootProject.file("LICENSE-SQLITE3MULTIPLECIPHERS"))
                    from(rootProject.file("NOTICE"))
                    from(rootProject.file("PATENTS.md"))
                }
            val normalizeBundleFileTimestamps =
                tasks.register<NormalizeBundleFileTimestampsTask>("normalizeBundleFileTimestamps") {
                    group = "distribution"
                    description =
                        "Applies the normalized public bundle timestamp to every staged bundle path before archiving."
                    dependsOn(stageCliBundle)
                    this.bundleRootDirectory.set(bundleRootDirectory)
                    this.normalizedArtifactEpochSeconds.set(
                        normalizedArtifactTimestamp.map(Instant::getEpochSecond),
                    )
                }

            val bundleArchiveTasks =
                registerCliBundleArchiveTasks(
                    bundleOperatingSystemId = bundleOperatingSystemId,
                    bundleArchiveInputTask = normalizeBundleFileTimestamps,
                    distributionDirectory = distributionDirectory,
                    bundleArchiveFileName = bundleArchiveFileName,
                    bundleWorkspaceDirectory = bundleWorkspaceDirectory,
                    bundleName = bundleName,
                    bundleSha256File = bundleSha256File,
                    bundleArchiveManifestFile = bundleArchiveManifestOutputFile,
                )

            tasks.named("assemble") {
                dependsOn(bundleArchiveTasks.manifestTask)
            }
        }
    }
}
