package dev.erst.fingrind.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class FinGrindRootConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            FinGrindFilesystemLayout.projectBuildDirectory(this)?.let { projectBuildDirectory ->
                layout.buildDirectory.set(projectBuildDirectory)
            }

            pluginManager.apply("base")
            pluginManager.apply("jacoco")
            pluginManager.apply("com.diffplug.spotless")

            val buildMetadata = FinGrindBuildMetadata.load(this)

            description = providers.gradleProperty("fingrindDescription").get()
            configureFinGrindArtifactRepositories()
            configurePinnedJacocoVersion()

            allprojects {
                group = providers.gradleProperty("group").get()
                version = providers.gradleProperty("version").get()
            }

            configureRootFormattingConventions()
            configureRootPythonAndSqlVerification(buildMetadata)

            val repositoryRootDirectory = layout.projectDirectory.asFile.toPath()
            val managedSqlitePackageId =
                DistributionContractReader.requiredSqliteSourcePackageId(repositoryRootDirectory)

            val managedSqlite =
                ManagedSqliteProvisioningLogic.register(
                    project = this,
                    repositoryRootDirectory = repositoryRootDirectory,
                    sqliteSourceDirectory = layout.projectDirectory.dir("third_party/sqlite/$managedSqlitePackageId"),
                    sqliteVersionValue =
                        DistributionContractReader.requiredMinimumSqliteVersion(repositoryRootDirectory),
                    sqlite3mcVersionValue =
                        DistributionContractReader.requiredSqlite3mcVersion(repositoryRootDirectory),
                    sourcePackageId = managedSqlitePackageId,
                )
            ManagedSqliteProvisioningRegistry.publish(this, managedSqlite)
            configureRootCoverageAggregation()
            configureRootJazzerVerification()
        }
    }
}
