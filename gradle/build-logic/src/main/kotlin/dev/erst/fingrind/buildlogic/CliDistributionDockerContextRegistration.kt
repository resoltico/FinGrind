package dev.erst.fingrind.buildlogic

import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.register

internal fun Project.registerCliDockerBuildContextTasks(
    shadowJarTask: org.gradle.api.tasks.TaskProvider<Jar>,
    writeRuntimeModuleList: org.gradle.api.tasks.TaskProvider<WriteRuntimeModuleListTask>,
    dockerBuildContextDirectory: Provider<Directory>,
    repositoryDockerBuildContextDirectory: Directory,
    mirrorRepositoryDockerBuildContext: Boolean,
    dockerBuildContextManifestOutputFile: Provider<RegularFile>,
    dockerBuildContextFiles: List<String>,
    dockerBuildContextSourceInputs: FileCollection,
    dockerManagedSqliteContractSource: Provider<java.nio.file.Path>,
    sqliteBundleHomeSystemProperty: String,
    containerRuntimeDistribution: String,
): org.gradle.api.tasks.TaskProvider<Sync> {
    val writeDockerBuildContextManifest =
        tasks.register<WriteDockerBuildContextManifestTask>("writeDockerBuildContextManifest") {
            group = "distribution"
            description =
                "Writes the manifest for the staged Docker build context plus the source fingerprint used to verify freshness."
            ownerTaskName.set("stageDockerBuildContext")
            fileNames.set(dockerBuildContextFiles)
            repositoryRootPath.set(rootProject.projectDir.toPath().toString())
            sourceFiles.from(dockerBuildContextSourceInputs)
            outputFile.set(dockerBuildContextManifestOutputFile)
        }

    val stageDockerBuildContext =
        tasks.register<Sync>("stageDockerBuildContext") {
            group = "distribution"
            description =
                "Stages the complete Docker build context consumed by the container image build."
            dependsOn(shadowJarTask)
            dependsOn(writeRuntimeModuleList)
            dependsOn(writeDockerBuildContextManifest)
            into(dockerBuildContextDirectory)

            from(writeDockerBuildContextManifest) {
                rename { "docker-build-context-manifest.json" }
            }
            from(rootProject.layout.projectDirectory.file("Dockerfile"))
            from(shadowJarTask.flatMap { it.archiveFile }) {
                rename { "fingrind.jar" }
            }
            from(writeRuntimeModuleList.flatMap { it.outputFile }) {
                rename { "runtime-modules.txt" }
            }
            from(layout.projectDirectory.dir("src/docker")) {
                include("docker-entrypoint.sh")
                filter(
                    mapOf(
                        "tokens" to
                            mapOf(
                                "bundleHomeSystemProperty" to sqliteBundleHomeSystemProperty,
                                "containerRuntimeDistribution" to containerRuntimeDistribution,
                            ),
                        "beginToken" to "{{",
                        "endToken" to "}}",
                    ),
                    ReplaceTokens::class.java,
                )
            }
            from(dockerManagedSqliteContractSource) {
                rename { "managed-sqlite-contract.json" }
            }
            from(dockerBuildContextSourceInputs) {
                into("source-root")
            }
        }

    val syncRepositoryDockerBuildContext =
        tasks.register<Sync>("syncRepositoryDockerBuildContext") {
            group = "distribution"
            description =
                "Mirrors the staged Docker build context into cli/build/docker-context for plain docker build consumers."
            dependsOn(stageDockerBuildContext)
            from(dockerBuildContextDirectory)
            into(repositoryDockerBuildContextDirectory)
            enabled = mirrorRepositoryDockerBuildContext
        }

    stageDockerBuildContext.configure {
        finalizedBy(syncRepositoryDockerBuildContext)
    }
    return stageDockerBuildContext
}
