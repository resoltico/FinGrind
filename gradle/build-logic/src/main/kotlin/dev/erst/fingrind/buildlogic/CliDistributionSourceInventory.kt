package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import org.gradle.api.Project
import org.gradle.api.file.FileCollection

object CliDistributionSourceInventory {
    private val sourceCheckoutArtifactSourceIncludePatterns =
        listOf(
            "build.gradle.kts",
            "settings.gradle.kts",
            "gradle.properties",
            "gradlew",
            "gradlew.bat",
            "gradle/fingrind-build.properties",
            "gradle/libs.versions.toml",
            "gradle/wrapper/**",
            "gradle/build-logic/**",
            "cli/build.gradle.kts",
            "cli/src/main/**",
            "contract/build.gradle.kts",
            "contract/src/main/**",
            "core/build.gradle.kts",
            "core/src/main/**",
            "executor/build.gradle.kts",
            "executor/src/main/**",
            "report-pdf/build.gradle.kts",
            "report-pdf/src/main/**",
            "sqlite/build.gradle.kts",
            "sqlite/src/main/**",
        )

    fun sourceCheckoutArtifactSourceFiles(
        project: Project,
        repositoryRootDirectory: Path,
    ): FileCollection =
        project.objects.fileCollection().from(
            project.rootProject.fileTree(repositoryRootDirectory.toFile()) {
                sourceCheckoutArtifactSourceIncludePatterns.forEach(::include)
            },
        )

    fun dockerBuildContextSourceFiles(
        project: Project,
        repositoryRootDirectory: Path,
        managedSqliteSourcePackageId: String,
    ): FileCollection =
        project.objects.fileCollection().from(
            project.rootProject.fileTree(repositoryRootDirectory.toFile()) {
                dockerBuildContextSourceIncludePatterns(managedSqliteSourcePackageId)
                    .forEach(::include)
            },
        )

    fun dockerBuildContextFiles(): List<String> =
        listOf(
            "Dockerfile",
            "docker-build-context-manifest.json",
            "docker-entrypoint.sh",
            "fingrind.jar",
            "managed-sqlite-contract.json",
            "runtime-modules.txt",
        )

    private fun dockerBuildContextSourceIncludePatterns(
        managedSqliteSourcePackageId: String,
    ): List<String> =
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
            "gradle/build-logic/**",
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
            "scripts/render-managed-sqlite-compiler-flags.py",
            "scripts/verify-docker-build-context.py",
            "sqlite/build.gradle.kts",
            "sqlite/src/main/**",
            "third_party/sqlite/$managedSqliteSourcePackageId/**",
        )
}
