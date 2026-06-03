package dev.erst.fingrind.buildlogic

import java.util.zip.ZipFile
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

internal fun Project.configureJavaRuntimeConventions(
    fingrindJavaVersion: Int,
    implementationVendor: String,
    implementationLicense: String,
    enforcedErrorProneChecks: List<String>,
) {
    val libs = versionCatalog()

    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(fingrindJavaVersion))
            modularity.inferModulePath.set(true)
            withSourcesJar()
        }
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        sourceSets.configureEach {
            val sourceSet = this
            val compileTaskProvider = tasks.named<JavaCompile>(sourceSet.compileJavaTaskName)
            val pruneTaskName =
                "prune${sourceSet.name.replaceFirstChar(Char::titlecase)}StaleJavaClassOutputs"
            val pruneTask =
                tasks.register<PruneStaleJavaClassOutputsTask>(pruneTaskName) {
                    description =
                        "Prunes stale Java class outputs owned by ${sourceSet.name} sources."
                    sourceDirectories.from(sourceSet.allJava.srcDirs)
                    classesDirectory.set(compileTaskProvider.flatMap { it.destinationDirectory })
                    sourceOwnerManifest.set(
                        layout.buildDirectory.file(
                            "intermediates/stale-java-class-owners/${sourceSet.name}.manifest",
                        ),
                    )
                    rerunTasksRequested.set(gradle.startParameter.isRerunTasks)
                }
            compileTaskProvider.configure {
                dependsOn(pruneTask)
            }
        }
        dependencies.add(
            "testImplementation",
            project.dependencies.platform(libs.library("junit-bom")),
        )
        dependencies.add("testImplementation", libs.library("junit-jupiter"))
        dependencies.add("testRuntimeOnly", libs.library("junit-platform-launcher"))
    }

    dependencies.add("errorprone", libs.library("errorprone-core"))
    dependencies.add("errorprone", libs.library("nullaway"))
    dependencies.add("compileOnly", libs.library("jspecify"))
    dependencies.add("testCompileOnly", libs.library("jspecify"))
    pluginManager.withPlugin("java-test-fixtures") {
        dependencies.add("testFixturesCompileOnly", libs.library("jspecify"))
    }

    tasks.withType<Jar>().configureEach {
        inputs.property("manifestImplementationTitle", project.name)
        inputs.property("manifestImplementationVersion", project.version.toString())
        inputs.property("manifestImplementationVendor", implementationVendor)
        inputs.property("manifestImplementationLicense", implementationLicense)
        val moduleDescriptorSource =
            project.layout.projectDirectory.file("src/main/java/module-info.java")
        inputs.file(moduleDescriptorSource)
            .optional()
            .withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.upToDateWhen {
            val descriptorFile = moduleDescriptorSource.asFile
            if (!descriptorFile.isFile) {
                true
            } else {
                jarContainsModuleDescriptor(archiveFile.orNull)
            }
        }
        manifest.attributes(
            mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to implementationVendor,
                "Implementation-License" to implementationLicense,
            ),
        )
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(fingrindJavaVersion)
        options.errorprone.disableWarningsInGeneratedCode.set(true)
        options.errorprone.option("NullAway:OnlyNullMarked", "true")
        options.errorprone.option("NullAway:JSpecifyMode", "true")
        options.errorprone.error(*enforcedErrorProneChecks.toTypedArray())
    }
}

private fun jarContainsModuleDescriptor(archiveFile: RegularFile?): Boolean {
    val jarFile = archiveFile?.asFile ?: return false
    if (!jarFile.isFile) {
        return false
    }
    ZipFile(jarFile).use { zipFile ->
        return zipFile.getEntry("module-info.class") != null
    }
}
