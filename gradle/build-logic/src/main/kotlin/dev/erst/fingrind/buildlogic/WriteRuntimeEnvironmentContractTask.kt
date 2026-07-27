package dev.erst.fingrind.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/** Writes the source-checkout Java major-version promise consumed by the public protocol catalog. */
abstract class WriteRuntimeEnvironmentContractTask : DefaultTask() {
    @get:Input
    abstract val sourceCheckoutJava: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeContract() {
        val destination = outputFile.get().asFile
        destination.parentFile.mkdirs()
        destination.writeText(
            """
            {
              "sourceCheckoutJava": ${DistributionTextRendering.jsonString(sourceCheckoutJava.get())}
            }
            """
                .trimIndent()
                + System.lineSeparator(),
        )
    }
}
