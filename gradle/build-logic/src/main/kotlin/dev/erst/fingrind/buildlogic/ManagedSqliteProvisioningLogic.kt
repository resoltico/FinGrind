package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

internal data class ManagedSqliteProvisioning(
    val classifier: String,
    val libraryFileName: String,
    val libraryPath: Provider<RegularFile>,
    val checksumPath: Provider<RegularFile>,
    val toolchainFingerprintPath: Provider<RegularFile>,
    val buildContractPath: Provider<RegularFile>,
    val prepareTask: TaskProvider<out Task>,
)

internal object ManagedSqliteProvisioningLogic {
    fun register(
        project: Project,
        repositoryRootDirectory: Path,
        sqliteSourceDirectory: Directory,
        sqliteVersionValue: String,
        sqlite3mcVersionValue: String,
        sourcePackageId: String,
    ): ManagedSqliteProvisioning {
        val hostBundleTarget =
            try {
                DistributionBundleTargetReader.hostBundleTarget(repositoryRootDirectory)
            } catch (_: IllegalStateException) {
                throw GradleException(
                    "FinGrind's managed SQLite build currently supports only declared bundle-layout targets. Detected host: ${System.getProperty("os.name")} / ${System.getProperty("os.arch")}",
                )
            }
        return registerLocalManagedSqliteTarget(
            project = project,
            repositoryRootDirectory = repositoryRootDirectory,
            sqliteSourceDirectory = sqliteSourceDirectory,
            sqliteVersionValue = sqliteVersionValue,
            sqlite3mcVersionValue = sqlite3mcVersionValue,
            sourcePackageId = sourcePackageId,
            bundleTarget = hostBundleTarget,
            taskName = "prepareManagedSqlite",
            sourceVerificationTaskName = "verifyManagedSqliteSource",
            toolchainProbeTaskName = "probeManagedSqliteToolchain",
            taskDescription =
                "Builds the managed SQLite $sqliteVersionValue / SQLite3 Multiple Ciphers $sqlite3mcVersionValue shared library for the current host.",
            provisioningPathPrefix = "managed-sqlite/${hostBundleTarget.classifier}",
        )
    }

    fun registerDockerContextTarget(
        project: Project,
        hostProvisioning: ManagedSqliteProvisioning,
        repositoryRootDirectory: Path,
        sqliteSourceDirectory: Directory,
        sqliteVersionValue: String,
        sqlite3mcVersionValue: String,
        sourcePackageId: String,
    ): ManagedSqliteProvisioning =
        registerDockerManagedSqliteTarget(
            project = project,
            hostProvisioning = hostProvisioning,
            repositoryRootDirectory = repositoryRootDirectory,
            sqliteSourceDirectory = sqliteSourceDirectory,
            sqliteVersionValue = sqliteVersionValue,
            sqlite3mcVersionValue = sqlite3mcVersionValue,
            sourcePackageId = sourcePackageId,
        )

    fun configureConsumers(project: Project, provisioning: ManagedSqliteProvisioning) {
        val repositoryRoot = project.rootProject.projectDir.toPath()
        val sourceCheckoutBuildRoot = project.rootProject.layout.buildDirectory.get().asFile.toPath()
        val sourceCheckoutRuntimeDistribution =
            DistributionContractReader.sourceCheckoutRuntimeDistribution(repositoryRoot)
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainRuntimeModulePath = sourceSets.getByName("main").runtimeClasspath
        project.tasks.withType<Test>().configureEach {
            dependsOn(provisioning.prepareTask)
            useModulePath(mainRuntimeModulePath)
            addSqliteNamedModule()
            enableSqliteNamedNativeAccess()
            systemProperty("fingrind.runtime.distribution", sourceCheckoutRuntimeDistribution)
            systemProperty("fingrind.source-checkout.root", repositoryRoot.toString())
            systemProperty("fingrind.source-checkout.build-root", sourceCheckoutBuildRoot.toString())
        }
        project.tasks.withType<JavaExec>().configureEach {
            dependsOn(provisioning.prepareTask)
            if (mainModule.orNull == null) {
                useModulePath(mainRuntimeModulePath)
                addSqliteNamedModule()
            }
            enableSqliteNamedNativeAccess()
            systemProperty("fingrind.runtime.distribution", sourceCheckoutRuntimeDistribution)
            systemProperty("fingrind.source-checkout.root", repositoryRoot.toString())
            systemProperty("fingrind.source-checkout.build-root", sourceCheckoutBuildRoot.toString())
        }
    }
}
