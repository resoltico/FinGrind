package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import org.gradle.api.Project
import org.gradle.api.file.FileCollection

object CliDistributionSourceInventory {
    fun dockerBuildContextSourceFiles(
        project: Project,
        repositoryRootDirectory: Path,
    ): FileCollection =
        project.objects.fileCollection().from(
            project.rootProject.fileTree(repositoryRootDirectory.toFile()) {
                dockerBuildContextSourceIncludePatterns().forEach(::include)
            },
        )

    fun dockerBuildContextFiles(): List<String> =
        listOf(
            "Dockerfile",
            "docker-build-context-manifest.json",
            "docker-entrypoint.sh",
            "fingrind.jar",
            "native-sqlite-format-boundary-probe.jar",
            "libsqlite3.so.0",
            "libsqlite3.so.0.sha256",
            "toolchain-fingerprint.json",
            "build-contract.json",
            "runtime-modules.txt",
        )

    private fun dockerBuildContextSourceIncludePatterns(): List<String> =
        listOf(
            "Dockerfile",
            "LICENSE",
            "LICENSE-APACHE-2.0",
            "LICENSE-SIL-OFL-1.1",
            "LICENSE-SQLITE3MULTIPLECIPHERS",
            "NOTICE",
            "PATENTS.md",
            "build.gradle.kts",
            "settings.gradle.kts",
            "gradle.properties",
            "gradlew",
            "gradlew.bat",
            "gradle/fingrind-build.properties",
            "gradle/libs.versions.toml",
            "gradle/wrapper/**",
            "gradle/build-logic/build.gradle.kts",
            "gradle/build-logic/settings.gradle.kts",
            "gradle/build-logic/src/**",
            "cli/build.gradle.kts",
            "cli/src/main/**",
            "cli/src/docker/**",
            "contract/build.gradle.kts",
            "contract/src/main/**",
            "core/build.gradle.kts",
            "core/src/main/**",
            "executor/build.gradle.kts",
            "executor/src/main/**",
            "report-pdf/build.gradle.kts",
            "report-pdf/src/main/**",
            "scripts/verify-docker-build-context.py",
            "scripts/release_smoke_workflow/field_matrix/NativeSqliteFormatBoundaryProbe.java",
            "sqlite/build.gradle.kts",
            "sqlite/src/main/**",
        )
}
