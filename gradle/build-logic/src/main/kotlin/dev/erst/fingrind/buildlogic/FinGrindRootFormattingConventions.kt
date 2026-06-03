package dev.erst.fingrind.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

internal fun Project.configureRootFormattingConventions() {
    extensions.configure<SpotlessExtension> {
        lineEndings = LineEnding.UNIX
        format("projectFiles") {
            target(
                fileTree(projectDir) {
                    include(
                        ".gitattributes",
                        ".gitignore",
                        ".dockerignore",
                        "gradle/sqlfluff/sqlfluff.cfg",
                        "Dockerfile",
                        "*.toml",
                        "**/*.gradle.kts",
                        "**/*.md",
                        "requirements*.txt",
                        "**/*.yml",
                        "gradle.properties",
                        "gradle/**/*.toml",
                        "docs/**/*.json",
                        "examples/**/*.json",
                    )
                    exclude(
                        "**/build/**",
                        "**/.claude/**",
                        "**/.gradle/**",
                        "**/.local/**",
                        "tmp/**",
                    )
                },
            )
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
