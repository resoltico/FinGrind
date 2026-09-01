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
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

private val REQUIRED_ADDITIONAL_RUNTIME_MODULES = listOf("jdk.crypto.ec", "jdk.unsupported")

class FinGrindCliDistributionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            val repositoryRootDirectory = rootProject.projectDir.toPath()
            val buildMetadata = FinGrindBuildMetadata.load(this)
            val fingrindJavaVersion = buildMetadata.javaVersion
            val javaToolchainService = project.extensions.getByType<JavaToolchainService>()
            val sourceCheckoutJavaLauncher =
                javaToolchainService.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(fingrindJavaVersion))
                }
            val sourceCheckoutJavaCompiler =
                javaToolchainService.compilerFor {
                    languageVersion.set(JavaLanguageVersion.of(fingrindJavaVersion))
                }
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
            val verificationBundleClassifier =
                providers.gradleProperty("fingrindVerificationTargetClassifier").orElse(
                    "windows-x86_64",
                )
            val bundleName =
                bundleClassifier.map { classifier ->
                    BundleStagingLayout.bundleName(project.version.toString(), classifier)
                }
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
            val nativeSqliteFormatBoundaryProbe =
                registerNativeSqliteFormatBoundaryProbe(
                    javaCompilerExecutable = sourceCheckoutJavaCompiler.map { it.executablePath },
                    javaVersion = fingrindJavaVersion,
                )
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
                    rootProject.layout.projectDirectory.file("LICENSE-CC0-1.0").asFile,
                    rootProject.layout.projectDirectory.file("LICENSE-SIL-OFL-1.1").asFile,
                    rootProject.layout.projectDirectory.file("LICENSE-SQLITE3MULTIPLECIPHERS").asFile,
                    rootProject.layout.projectDirectory.file("LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY").asFile,
                    rootProject.layout.projectDirectory.file("NOTICE").asFile,
                    rootProject.layout.projectDirectory.file("NOTICE-ZULU-26.32.203").asFile,
                    rootProject.layout.projectDirectory.file("PATENTS.md").asFile,
                    rootProject.layout.projectDirectory.file("SOURCE_OFFER.md").asFile,
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
            BundleHostTargetAdmission.requireHostNative(
                requestedTarget = bundleTarget,
                hostTarget = hostBundleTarget,
            )
            val bundleStagingLayout =
                BundleStagingLayout.plan(
                    version = project.version.toString(),
                    bundleTarget = bundleTarget,
                )
            val bundleArchiveFileName = providers.provider { bundleStagingLayout.archiveFileName }
            val bundleSha256File =
                bundleArchiveFileName.flatMap { fileName ->
                    layout.buildDirectory.file("distributions/$fileName.sha256")
                }
            val normalizedArtifactTimestamp =
                providers.provider {
                    NormalizedArtifactTimestampResolver.resolve(repositoryRootDirectory)
                }
            val bundleTemplateProperties =
                BundleStagingTemplateProperties.resolve(
                    projectRootDirectory = repositoryRootDirectory,
                    version = project.version.toString(),
                    bundleStagingLayout = bundleStagingLayout,
                )

            registerTargetBundleLayoutVerification(
                repositoryRootDirectory = repositoryRootDirectory,
                bundleStagingLayout = bundleStagingLayout,
                buildMetadata = buildMetadata,
                verificationBundleClassifier = verificationBundleClassifier,
                normalizedArtifactTimestamp = normalizedArtifactTimestamp,
            )

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
                    nativeAccessModules.set(CLI_NATIVE_ACCESS_MODULE)
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
                    additionalModules.set(REQUIRED_ADDITIONAL_RUNTIME_MODULES)
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
                    nativeSqliteFormatBoundaryProbeTask = nativeSqliteFormatBoundaryProbe.packageTask,
                    nativeSqliteFormatBoundaryProbeJar = nativeSqliteFormatBoundaryProbe.archiveFile,
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
                    dependsOn(nativeSqliteFormatBoundaryProbe.packageTask)
                    dependsOn(hostManagedSqlite.prepareTask)
                    dependsOn(createRuntimeImage)
                    dependsOn(writeBundleManifest)
                    into(bundleRootDirectory)
                    inputs.properties(bundleTemplateProperties)

                    from(layout.projectDirectory.dir("src/bundle")) {
                        include(*bundleStagingLayout.launcherSourceIncludePaths.toTypedArray())
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
                        include(*bundleStagingLayout.rootTemplateSourceIncludePaths.toTypedArray())
                        expand(bundleTemplateProperties)
                    }
                    from(bundleManifestOutputFile) {
                        rename { bundleStagingLayout.bundleManifestPath }
                    }
                    from(shadowJarArchiveFile) {
                        into(bundleStagingLayout.applicationJarPath.substringBeforeLast('/'))
                        rename { bundleStagingLayout.applicationJarPath.substringAfterLast('/') }
                    }
                    from(nativeSqliteFormatBoundaryProbe.archiveFile) {
                        into(bundleStagingLayout.nativeFormatBoundaryProbePath.substringBeforeLast('/'))
                        rename {
                            bundleStagingLayout.nativeFormatBoundaryProbePath.substringAfterLast('/')
                        }
                    }
                    from(runtimeImageDirectory) {
                        into(bundleStagingLayout.runtimeDirectoryPath)
                    }
                    from(hostManagedSqlite.libraryPath) {
                        into(bundleStagingLayout.nativeDirectoryPath)
                        rename { bundleStagingLayout.nativeLibraryFileName }
                    }
                    from(hostManagedSqlite.checksumPath) {
                        into(bundleStagingLayout.nativeDirectoryPath)
                        rename { bundleStagingLayout.nativeLibraryChecksumPath.substringAfterLast('/') }
                    }
                    from(hostManagedSqlite.toolchainFingerprintPath) {
                        into(bundleStagingLayout.nativeDirectoryPath)
                        rename { bundleStagingLayout.toolchainFingerprintPath.substringAfterLast('/') }
                    }
                    from(hostManagedSqlite.buildContractPath) {
                        into(bundleStagingLayout.nativeDirectoryPath)
                        rename { bundleStagingLayout.nativeBuildContractPath.substringAfterLast('/') }
                    }
                    bundleStagingLayout.legalDocumentPaths.forEach { legalDocumentPath ->
                        from(rootProject.file(legalDocumentPath))
                    }
                }
            val validateCliBundleArchiveMembers =
                tasks.register<ValidateBundleArchiveMembersTask>(
                    "validateCliBundleArchiveMembers",
                ) {
                    group = "verification"
                    description =
                        "Validates every staged bundle archive member for portable target extraction."
                    dependsOn(stageCliBundle)
                    this.bundleRootDirectory.set(bundleRootDirectory)
                    archiveRootName.set(bundleStagingLayout.bundleName)
                    archiveFormat.set(bundleStagingLayout.archiveFormat)
                }
            val normalizeBundleFileTimestamps =
                tasks.register<NormalizeBundleFileTimestampsTask>("normalizeBundleFileTimestamps") {
                    group = "distribution"
                    description =
                        "Applies the normalized public bundle timestamp to every staged bundle path before archiving."
                    dependsOn(validateCliBundleArchiveMembers)
                    this.bundleRootDirectory.set(bundleRootDirectory)
                    this.normalizedArtifactEpochSeconds.set(
                        normalizedArtifactTimestamp.map(Instant::getEpochSecond),
                    )
                }

            val bundleArchiveTasks =
                registerCliBundleArchiveTasks(
                    bundleArchiveFormat = bundleStagingLayout.archiveFormat,
                    bundleArchiveInputTask = normalizeBundleFileTimestamps,
                    bundleArchiveMemberValidationTask = validateCliBundleArchiveMembers,
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
