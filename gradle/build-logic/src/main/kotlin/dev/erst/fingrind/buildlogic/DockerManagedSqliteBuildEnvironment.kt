package dev.erst.fingrind.buildlogic

internal object DockerManagedSqliteBuildEnvironment {
    const val builderImage =
        "azul/zulu-openjdk-alpine:26.0.1-jdk@sha256:46db716f3d5ca5ed35db59c0511b4f7847c4c5c89f53e7fb57fdab0573a762f6"
    const val buildBasePackage = "build-base=0.5-r3"
    const val pythonPackage = "python3=3.12.13-r0"

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
