package dev.erst.fingrind.buildlogic

internal object DockerManagedSqliteBuildEnvironment {
    private const val alpineBaseImage =
        "alpine:3.24@sha256:a2d49ea686c2adfe3c992e47dc3b5e7fa6e6b5055609400dc2acaeb241c829f4"
    const val builderImage = alpineBaseImage
    const val runtimeImage = alpineBaseImage
    const val runtimeLibStdCppPackage = "libstdc++"
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
