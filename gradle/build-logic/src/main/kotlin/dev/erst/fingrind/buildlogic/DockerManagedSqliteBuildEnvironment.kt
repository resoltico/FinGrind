package dev.erst.fingrind.buildlogic

internal object DockerManagedSqliteBuildEnvironment {
    private const val alpineBaseImage =
        "alpine:3.24@sha256:28bd5fe8b56d1bd048e5babf5b10710ebe0bae67db86916198a6eec434943f8b"
    const val builderImage = alpineBaseImage
    const val runtimeImage = alpineBaseImage
    const val builderBinutilsPackage = "binutils"
    const val buildBasePackage = "build-base"
    const val pythonPackage = "python3"

    fun dockerPlatformForArchitecture(architectureId: String): String =
        when (architectureId) {
            "x86_64" -> "linux/amd64"
            "aarch64" -> "linux/arm64"
            else ->
                throw IllegalStateException(
                    "Docker-managed SQLite does not support Linux target architecture $architectureId.",
                )
        }
}
