package dev.erst.fingrind.buildlogic

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.Instant
import java.util.Comparator
import java.util.HexFormat
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * Verifies one target bundle layout without building a native runtime or an archive.
 *
 * The task materializes only sentinel payloads in Gradle's task-temporary directory, then invokes
 * the canonical archive-contract verifier in structural mode. Its sole persistent output is a
 * verification receipt; it never writes a distributable archive or a staged public bundle.
 */
abstract class VerifyTargetBundleLayoutTask
    @Inject
    constructor(
        private val execOperations: ExecOperations,
    ) : DefaultTask() {
        init {
            outputs.upToDateWhen { false }
        }

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val contractFiles: ConfigurableFileCollection

        @get:InputDirectory
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val bundleSourceDirectory: DirectoryProperty

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val legalDocumentFiles: ConfigurableFileCollection

        @get:InputFile
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val verifierScript: RegularFileProperty

        @get:Internal
        abstract val projectRootDirectoryPath: Property<String>

        @get:Input
        abstract val applicationName: Property<String>

        @get:Input
        abstract val versionText: Property<String>

        @get:Input
        abstract val bundleClassifier: Property<String>

        @get:Input
        abstract val normalizedArtifactTimestampUtc: Property<String>

        @get:Input
        abstract val pythonExecutable: Property<String>

        @get:OutputFile
        abstract val verificationReceiptFile: RegularFileProperty

        @TaskAction
        fun verifyTargetBundleLayout() {
            val repositoryRootDirectory = Path.of(projectRootDirectoryPath.get())
            val syntheticBundleRoot =
                SyntheticTargetBundleLayout.materialize(
                    repositoryRootDirectory = repositoryRootDirectory,
                    bundleSourceDirectory = bundleSourceDirectory.get().asFile.toPath(),
                    destinationDirectory = temporaryDir.toPath().resolve("synthetic-target-layout"),
                    applicationName = applicationName.get(),
                    version = versionText.get(),
                    bundleClassifier = bundleClassifier.get(),
                    normalizedArtifactTimestampUtc = normalizedArtifactTimestampUtc.get(),
                )

            execOperations.exec {
                workingDir = repositoryRootDirectory.toFile()
                commandLine(
                    pythonExecutable.get(),
                    verifierScript.get().asFile.absolutePath,
                    "--repo-root",
                    repositoryRootDirectory.toString(),
                    "--bundle-root",
                    syntheticBundleRoot.toString(),
                    "--structural-only",
                )
            }

            verificationReceiptFile.get().asFile.apply {
                parentFile.mkdirs()
                writeText(
                    buildString {
                        appendLine("mode=synthetic-target-layout")
                        appendLine("target=${bundleClassifier.get()}")
                        appendLine("archive=not-created")
                    },
                    StandardCharsets.UTF_8,
                )
            }
            logger.lifecycle(
                "Verified synthetic {} bundle layout through the canonical structural contract; no archive was created.",
                bundleClassifier.get(),
            )
        }
    }

internal object SyntheticTargetBundleLayout {
    private const val APPLICATION_MODULE_NAME = "dev.erst.fingrind.cli"
    private const val APPLICATION_MAIN_CLASS = "dev.erst.fingrind.cli.App"

    private val nativeFormatBoundaryProbeEntries =
        listOf(
            "NativeSqliteFormatBoundaryProbe.class",
            "NativeSqliteFormatBoundaryProbe\$Arguments.class",
            "NativeSqliteFormatBoundaryProbe\$ProbeFailure.class",
            "NativeSqliteFormatBoundaryProbe\$Sqlite.class",
        )

    fun materialize(
        repositoryRootDirectory: Path,
        bundleSourceDirectory: Path,
        destinationDirectory: Path,
        applicationName: String,
        version: String,
        bundleClassifier: String,
        normalizedArtifactTimestampUtc: String,
    ): Path {
        recreateDirectory(destinationDirectory)
        val layout =
            BundleStagingLayout.plan(
                projectRootDirectory = repositoryRootDirectory,
                version = version,
                classifier = bundleClassifier,
            )
        val templateProperties =
            BundleStagingTemplateProperties.resolve(
                projectRootDirectory = repositoryRootDirectory,
                version = version,
                bundleStagingLayout = layout,
            )

        copyRootTemplates(bundleSourceDirectory, destinationDirectory, layout, templateProperties)
        copyLegalDocuments(repositoryRootDirectory, destinationDirectory, layout)
        writeBundleManifest(
            destinationDirectory = destinationDirectory,
            repositoryRootDirectory = repositoryRootDirectory,
            applicationName = applicationName,
            version = version,
            bundleClassifier = bundleClassifier,
            normalizedArtifactTimestampUtc = normalizedArtifactTimestampUtc,
            layout = layout,
        )
        writeApplicationJar(destinationDirectory.resolve(layout.applicationJarPath))
        writeNativeFormatBoundaryProbe(destinationDirectory.resolve(layout.nativeFormatBoundaryProbePath))
        writeSentinelFile(
            destinationDirectory.resolve(layout.runtimeJavaPath),
            "synthetic target runtime sentinel; structural verification only\n",
        )
        writeSyntheticRuntimeMetadata(destinationDirectory, layout)
        val nativeLibrary = destinationDirectory.resolve(layout.nativeLibraryPath)
        writeSentinelFile(
            nativeLibrary,
            "synthetic target native-library sentinel; structural verification only\n",
        )
        ManagedSqliteArtifactSupport.writeChecksumFile(
            outputLibraryFile = nativeLibrary.toFile(),
            checksumOutputFile = destinationDirectory.resolve(layout.nativeLibraryChecksumPath).toFile(),
        )
        writeSentinelFile(
            destinationDirectory.resolve(layout.toolchainFingerprintPath),
            "{\"synthetic\":true,\"purpose\":\"target-layout-contract\"}\n",
        )
        writeSentinelFile(
            destinationDirectory.resolve(layout.nativeBuildContractPath),
            "{\"synthetic\":true,\"purpose\":\"target-layout-contract\"}\n",
        )
        require(layout.requiredArchiveFilePaths.all { archivePath ->
            Files.isRegularFile(destinationDirectory.resolve(archivePath))
        }) {
            "Synthetic target layout did not materialize every required bundle path for $bundleClassifier."
        }
        normalizeTimestamps(destinationDirectory, normalizedArtifactTimestampUtc)
        return destinationDirectory
    }

    private fun copyRootTemplates(
        bundleSourceDirectory: Path,
        destinationDirectory: Path,
        layout: BundleStagingPlan,
        templateProperties: Map<String, String>,
    ) {
        layout.rootTemplateSourceIncludePaths.forEach { sourcePath ->
            val source = bundleSourceDirectory.resolve("root").resolve(sourcePath)
            val destination = destinationDirectory.resolve(sourcePath)
            writeText(destination, renderRootTemplate(Files.readString(source), templateProperties))
        }
        val launcherSourcePath =
            layout.launcherTemplateSourcePath.removePrefix("src/bundle/").also { path ->
                require(path != layout.launcherTemplateSourcePath) {
                    "Synthetic target layout received an invalid launcher source path: ${layout.launcherTemplateSourcePath}."
                }
            }
        val launcher = renderLauncherTemplate(
            Files.readString(bundleSourceDirectory.resolve(launcherSourcePath)),
            templateProperties,
        )
        require("{{" !in launcher) {
            "Synthetic target layout left one launcher template placeholder unresolved."
        }
        writeText(destinationDirectory.resolve(layout.launcherPath), launcher)
    }

    private fun copyLegalDocuments(
        repositoryRootDirectory: Path,
        destinationDirectory: Path,
        layout: BundleStagingPlan,
    ) {
        layout.legalDocumentPaths.forEach { legalDocumentPath ->
            val source = repositoryRootDirectory.resolve(legalDocumentPath)
            val destination = destinationDirectory.resolve(legalDocumentPath)
            Files.createDirectories(destination.parent)
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun writeBundleManifest(
        destinationDirectory: Path,
        repositoryRootDirectory: Path,
        applicationName: String,
        version: String,
        bundleClassifier: String,
        normalizedArtifactTimestampUtc: String,
        layout: BundleStagingPlan,
    ) {
        writeText(
            destinationDirectory.resolve(layout.bundleManifestPath),
            BundleManifestRenderer.renderBundleManifest(
                projectRootDirectory = repositoryRootDirectory,
                applicationName = applicationName,
                version = version,
                bundleClassifier = bundleClassifier,
                normalizedArtifactTimestampUtc = normalizedArtifactTimestampUtc,
            ),
        )
    }

    private fun writeApplicationJar(destination: Path) {
        Files.createDirectories(destination.parent)
        val manifest =
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes.putValue("Automatic-Module-Name", APPLICATION_MODULE_NAME)
                mainAttributes[Attributes.Name.MAIN_CLASS] = APPLICATION_MAIN_CLASS
            }
        JarOutputStream(Files.newOutputStream(destination), manifest).use { output ->
            listOf(
                    "META-INF/LICENSE",
                    "META-INF/NOTICE",
                    "META-INF/NOTICE-ZULU-26.32.203",
                    "META-INF/LICENSE-APACHE-2.0",
                    "META-INF/LICENSE-CC0-1.0",
                    "META-INF/LICENSE-SIL-OFL-1.1",
                    "META-INF/LICENSE-SQLITE3MULTIPLECIPHERS",
                    "META-INF/LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY",
                    "META-INF/SOURCE_OFFER.md",
                )
                .forEach { entryName ->
                    output.putNextEntry(JarEntry(entryName))
                    output.write("synthetic legal sentinel\n".toByteArray(StandardCharsets.UTF_8))
                    output.closeEntry()
                }
        }
    }

    private fun writeSyntheticRuntimeMetadata(
        destinationDirectory: Path,
        layout: BundleStagingPlan,
    ) {
        writeSentinelFile(
            destinationDirectory.resolve(layout.runtimeReleasePath),
            "JAVA_VERSION=\"26.0.2.1\"\nMODULES=\"java.base\"\n",
        )
        writeSentinelFile(
            destinationDirectory.resolve(layout.runtimeSourceJdkReleasePath),
            "IMPLEMENTOR=\"Azul Systems, Inc.\"\n" +
                "IMPLEMENTOR_VERSION=\"Zulu26.32+203-CA\"\n" +
                "JAVA_RUNTIME_VERSION=\"26.0.2.1+1\"\n" +
                "JAVA_VERSION=\"26.0.2.1\"\n" +
                "OS_ARCH=\"x86_64\"\n" +
                "OS_NAME=\"Windows\"\n" +
                "SOURCE=\".:git:synthetic\"\n",
        )
        writeSentinelFile(
            destinationDirectory.resolve(layout.runtimeRequestedModulesPath),
            "java.base\n",
        )
        val legalDirectory =
            destinationDirectory.resolve(layout.runtimeDirectoryPath).resolve("legal")
        val legalFiles =
            mapOf(
                "java.base/LICENSE" to "GNU General Public License\n",
                "java.base/ADDITIONAL_LICENSE_INFO" to "Classpath Exception\n",
                "java.base/ASSEMBLY_EXCEPTION" to "OPENJDK ASSEMBLY EXCEPTION\n",
            )
        legalFiles.forEach { (relativePath, contents) ->
            writeSentinelFile(legalDirectory.resolve(relativePath), contents)
        }
        val legalIndex =
            buildString {
                legalFiles.keys.sorted().forEach { relativePath ->
                    val legalFile = legalDirectory.resolve(relativePath)
                    append(sha256(legalFile)).append("  ").append(relativePath).append('\n')
                }
            }
        writeSentinelFile(
            destinationDirectory.resolve(layout.runtimeLegalIndexPath),
            legalIndex,
        )
    }

    private fun writeNativeFormatBoundaryProbe(destination: Path) {
        Files.createDirectories(destination.parent)
        JarOutputStream(Files.newOutputStream(destination)).use { output ->
            nativeFormatBoundaryProbeEntries.forEach { entryName ->
                output.putNextEntry(JarEntry(entryName))
                output.write(byteArrayOf(0))
                output.closeEntry()
            }
        }
    }

    private fun writeSentinelFile(destination: Path, contents: String) {
        Files.createDirectories(destination.parent)
        writeText(destination, contents)
    }

    private fun sha256(path: Path): String =
        HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)),
        )

    private fun writeText(destination: Path, contents: String) {
        Files.createDirectories(destination.parent)
        Files.writeString(destination, contents, StandardCharsets.UTF_8)
    }

    private fun renderRootTemplate(
        template: String,
        properties: Map<String, String>,
    ): String {
        val rendered =
            properties.entries.fold(template) { current, (name, value) ->
                current.replace("${'$'}{$name}", value)
            }
        require("${'$'}{" !in rendered) {
            "Synthetic target layout left one root-template placeholder unresolved."
        }
        return rendered
    }

    private fun renderLauncherTemplate(
        template: String,
        properties: Map<String, String>,
    ): String =
        properties.entries.fold(template) { current, (name, value) ->
            current.replace("{{$name}}", value)
        }

    private fun normalizeTimestamps(destinationDirectory: Path, normalizedArtifactTimestampUtc: String) {
        val normalizedTimestamp = FileTime.from(Instant.parse(normalizedArtifactTimestampUtc))
        Files.walk(destinationDirectory).use { paths ->
            paths
                .sorted(Comparator.reverseOrder())
                .forEach { path -> Files.setLastModifiedTime(path, normalizedTimestamp) }
        }
    }

    private fun recreateDirectory(destinationDirectory: Path) {
        if (Files.exists(destinationDirectory)) {
            Files.walk(destinationDirectory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
        Files.createDirectories(destinationDirectory)
    }
}
