package dev.erst.fingrind.buildlogic

internal object DockerManagedSqliteBuildEnvironment {
    const val builderImage =
        "azul/zulu-openjdk-alpine:26.0.1-jdk@sha256:d5514973a10f0dbdf3c18199465713176316a60ee032d19adacd4812588b611b"
    const val runtimeImage =
        "alpine:3.24@sha256:a2d49ea686c2adfe3c992e47dc3b5e7fa6e6b5055609400dc2acaeb241c829f4"
    const val runtimeLibStdCppPackage = "libstdc++=15.2.0-r5"
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
