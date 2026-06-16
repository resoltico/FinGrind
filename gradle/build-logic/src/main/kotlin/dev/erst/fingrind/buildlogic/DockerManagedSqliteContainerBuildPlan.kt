package dev.erst.fingrind.buildlogic

import java.nio.file.Path

internal object DockerManagedSqliteContainerBuildPlan {
    private const val INPUT_MOUNT_TARGET = "/input"
    private const val OUTPUT_MOUNT_TARGET = "/output"
    private const val BUILD_ENVIRONMENT = "docker-run"

    fun dockerRunCommand(
        platform: String,
        inputDirectory: Path,
        outputDirectory: Path,
        builderImage: String,
        buildBasePackage: String,
        pythonPackage: String,
        sourceFileName: String,
        architectureId: String,
        sqliteVersion: String,
        requiredCompileOptions: List<String>,
        requiresSecureMemorySupport: Boolean,
        unixCompilerHardeningFlags: List<String>,
        linuxLinkerHardeningFlags: List<String>,
    ): List<String> =
        listOf(
            "docker",
            "run",
            "--pull=always",
            "--rm",
            "--platform",
            platform,
            "--mount",
            "type=bind,source=${inputDirectory.toAbsolutePath()},target=$INPUT_MOUNT_TARGET,readonly",
            "--mount",
            "type=bind,source=${outputDirectory.toAbsolutePath()},target=$OUTPUT_MOUNT_TARGET",
            builderImage,
            "sh",
            "-euxc",
            compileScript(
                builderImage = builderImage,
                buildBasePackage = buildBasePackage,
                pythonPackage = pythonPackage,
                sourceFileName = sourceFileName,
                architectureId = architectureId,
                sqliteVersion = sqliteVersion,
                requiredCompileOptions = requiredCompileOptions,
                requiresSecureMemorySupport = requiresSecureMemorySupport,
                unixCompilerHardeningFlags = unixCompilerHardeningFlags,
                linuxLinkerHardeningFlags = linuxLinkerHardeningFlags,
            ),
        )

    private fun compileScript(
        builderImage: String,
        buildBasePackage: String,
        pythonPackage: String,
        sourceFileName: String,
        architectureId: String,
        sqliteVersion: String,
        requiredCompileOptions: List<String>,
        requiresSecureMemorySupport: Boolean,
        unixCompilerHardeningFlags: List<String>,
        linuxLinkerHardeningFlags: List<String>,
    ): String {
        val packages = listOf(buildBasePackage, pythonPackage)
        val packagesJson =
            packages.joinToString(prefix = "[", postfix = "]") { packageName ->
                quoted(packageName)
            }
        val compilerCommand =
            compilerFlags(
                sourceFileName = sourceFileName,
                requiredCompileOptions = requiredCompileOptions,
                requiresSecureMemorySupport = requiresSecureMemorySupport,
                unixCompilerHardeningFlags = unixCompilerHardeningFlags,
                linuxLinkerHardeningFlags = linuxLinkerHardeningFlags,
            ).joinToString(" ") { flag ->
                shellQuote(flag)
            }
        return """
            |mkdir -p $OUTPUT_MOUNT_TARGET
            |apk add --no-cache ${packages.joinToString(" ") { packageName -> shellQuote(packageName) }}
            |$compilerCommand
            |python3 - <<'PY'
            |import json
            |import pathlib
            |import subprocess
            |
            |def run(*command: str) -> str:
            |    completed = subprocess.run(command, check=True, text=True, capture_output=True)
            |    return completed.stdout.strip()
            |
            |toolchain = {
            |    "compilerCommand": "cc",
            |    "compilerExecutable": run("sh", "-lc", "command -v cc"),
            |    "compilerVersion": run("cc", "--version"),
            |    "targetTriple": run("cc", "-dumpmachine"),
            |    "linkerVersion": run("ld", "--version"),
            |    "sdkOrSysroot": "",
            |    "operatingSystemId": "linux",
            |    "architectureId": ${quoted(architectureId)},
            |    "buildEnvironment": ${quoted(BUILD_ENVIRONMENT)},
            |    "builderImage": ${quoted(builderImage)},
            |    "packages": $packagesJson,
            |    "sqliteVersion": ${quoted(sqliteVersion)},
            |}
            |pathlib.Path("$OUTPUT_MOUNT_TARGET/toolchain-fingerprint.json").write_text(
            |    json.dumps(toolchain, indent=2) + "\n",
            |    encoding="utf-8",
            |)
            |PY
            |""".trimMargin()
    }

    private fun compilerFlags(
        sourceFileName: String,
        requiredCompileOptions: List<String>,
        requiresSecureMemorySupport: Boolean,
        unixCompilerHardeningFlags: List<String>,
        linuxLinkerHardeningFlags: List<String>,
    ): List<String> =
        buildList {
            add("cc")
            add("-O2")
            add("-fPIC")
            addAll(unixCompilerHardeningFlags)
            addAll(
                ManagedSqliteArtifactSupport.unixCompilerDefines(
                    requiredCompileOptions,
                    requiresSecureMemorySupport,
                ),
            )
            add("-shared")
            add("-Wl,-soname,libsqlite3.so.0")
            addAll(linuxLinkerHardeningFlags)
            add("-o")
            add("$OUTPUT_MOUNT_TARGET/libsqlite3.so.0")
            add("$INPUT_MOUNT_TARGET/$sourceFileName")
            add("-I$INPUT_MOUNT_TARGET")
            add("-ldl")
            add("-lpthread")
        }

    private fun shellQuote(value: String): String =
        buildString {
            append('\'')
            value.forEach { character ->
                if (character == '\'') {
                    append("'\"'\"'")
                } else {
                    append(character)
                }
            }
            append('\'')
        }

    private fun quoted(value: String): String =
        buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
            append('"')
        }
}
