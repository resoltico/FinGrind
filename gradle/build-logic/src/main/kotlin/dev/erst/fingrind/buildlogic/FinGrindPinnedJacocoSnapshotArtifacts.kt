package dev.erst.fingrind.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.kotlin.dsl.configure

private const val PREPARE_JACOCO_SNAPSHOT_ARTIFACTS_TASK = "prepareJacocoSnapshotArtifacts"
private const val ASM_VERSION = "9.10.1"

internal fun Project.configurePinnedJacocoSnapshotArtifacts(
    buildMetadata: FinGrindBuildMetadata.Values,
) {
    val prepareArtifactsTask =
        rootProject.prepareJacocoSnapshotArtifactsTask(buildMetadata)

    extensions.configure(JacocoPluginExtension::class.java) {
        toolVersion = buildMetadata.jacocoSnapshotBuildLabel
    }

    dependencies.add(
        "jacocoAgent",
        files(prepareArtifactsTask.flatMap { it.agentJarFile }).builtBy(prepareArtifactsTask),
    )
    dependencies.add(
        "jacocoAnt",
        files(
                prepareArtifactsTask.flatMap { it.antJarFile },
                prepareArtifactsTask.flatMap { it.coreJarFile },
                prepareArtifactsTask.flatMap { it.reportJarFile },
                prepareArtifactsTask.flatMap { it.agentJarFile },
            )
            .builtBy(prepareArtifactsTask),
    )
    dependencies.add("jacocoAnt", "org.ow2.asm:asm:$ASM_VERSION")
    dependencies.add("jacocoAnt", "org.ow2.asm:asm-commons:$ASM_VERSION")
    dependencies.add("jacocoAnt", "org.ow2.asm:asm-tree:$ASM_VERSION")
}

private fun Project.prepareJacocoSnapshotArtifactsTask(
    buildMetadata: FinGrindBuildMetadata.Values,
) = if (rootProject.tasks.names.contains(PREPARE_JACOCO_SNAPSHOT_ARTIFACTS_TASK)) {
    rootProject.tasks.named<PrepareJacocoSnapshotArtifactsTask>(
        PREPARE_JACOCO_SNAPSHOT_ARTIFACTS_TASK,
    )
} else {
    rootProject.tasks.register<PrepareJacocoSnapshotArtifactsTask>(
        PREPARE_JACOCO_SNAPSHOT_ARTIFACTS_TASK,
    ) {
        description =
            "Downloads the exact JaCoCo snapshot artifacts pinned in gradle/fingrind-build.properties."
        group = "verification"
        snapshotBaseVersion.set(buildMetadata.jacocoSnapshotBaseVersion)
        resolvedVersion.set(buildMetadata.jacocoSnapshotResolvedVersion)

        val artifactDirectory =
            rootProject.layout.buildDirectory.dir(
                "tools/jacoco/${buildMetadata.jacocoSnapshotBuildLabel}",
            )
        val resolvedVersion = buildMetadata.jacocoSnapshotResolvedVersion
        agentJarFile.set(artifactDirectory.map { it.file("org.jacoco.agent-$resolvedVersion.jar") })
        antJarFile.set(artifactDirectory.map { it.file("org.jacoco.ant-$resolvedVersion.jar") })
        coreJarFile.set(artifactDirectory.map { it.file("org.jacoco.core-$resolvedVersion.jar") })
        reportJarFile.set(artifactDirectory.map { it.file("org.jacoco.report-$resolvedVersion.jar") })
    }
}
