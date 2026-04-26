package dev.erst.fingrind.buildlogic

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class WriteBundleManifestTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contractFiles: ConfigurableFileCollection

    @get:Internal
    abstract val projectRootDirectoryPath: Property<String>

    @get:Input
    abstract val applicationName: Property<String>

    @get:Input
    abstract val versionText: Property<String>

    @get:Input
    abstract val bundleClassifier: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeBundleManifest() {
        val document =
            BundleManifestRenderer.renderBundleManifest(
                projectRootDirectory = Path.of(projectRootDirectoryPath.get()),
                applicationName = applicationName.get(),
                version = versionText.get(),
                bundleClassifier = bundleClassifier.get(),
            )
        val destination = outputFile.get().asFile
        destination.parentFile.mkdirs()
        destination.writeText(document, StandardCharsets.UTF_8)
    }
}
