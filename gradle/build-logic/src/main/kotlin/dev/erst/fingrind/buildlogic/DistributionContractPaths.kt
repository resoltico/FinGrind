package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path

internal object DistributionContractPaths {
    const val CONTRACT_SCHEMA_KEYS_PATH =
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/contract-schema-keys.json"
    const val PUBLIC_DISTRIBUTION_CONTRACT_PATH =
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/public-distribution-contract.json"
    const val MANAGED_SQLITE_CONTRACT_PATH =
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/managed-sqlite-contract.json"
    const val BUNDLE_LAYOUT_CONTRACT_PATH =
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/bundle-layout-contract.json"
    const val OPERATION_ID_CONTRACT_PATH =
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/operation-id-contract.json"
    const val RUNTIME_SURFACE_CONTRACT_PATH =
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/runtime-surface-contract.json"

    fun requiredContractFiles(projectRootDirectory: Path): List<Path> =
        listOf(
            CONTRACT_SCHEMA_KEYS_PATH,
            PUBLIC_DISTRIBUTION_CONTRACT_PATH,
            MANAGED_SQLITE_CONTRACT_PATH,
            BUNDLE_LAYOUT_CONTRACT_PATH,
            OPERATION_ID_CONTRACT_PATH,
            RUNTIME_SURFACE_CONTRACT_PATH,
        ).map { relativePath -> contractPath(projectRootDirectory, relativePath) }

    fun contractPath(projectRootDirectory: Path, relativePath: String): Path =
        sequenceOf(
                projectRootDirectory.resolve(relativePath),
                projectRootDirectory.resolve("..").resolve(relativePath),
            )
            .map(Path::normalize)
            .firstOrNull(Files::isRegularFile)
            ?: throw IllegalStateException(
                "Missing contract resource $relativePath for $projectRootDirectory.",
            )
}
