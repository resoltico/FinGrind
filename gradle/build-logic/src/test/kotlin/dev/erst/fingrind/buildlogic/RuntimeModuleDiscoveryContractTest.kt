package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RuntimeModuleDiscoveryContractTest {
    @Test
    fun allowedMissingDependencyPrefixes_failClosedOnMissingNullMalformedDuplicateBlankAndEmptyLists() {
        assertAllowedMissingDependencyPrefixFailure(
            null,
            "Missing required contract property allowedMissingDependencyPrefixes in ${DistributionContractPaths.RUNTIME_MODULE_DISCOVERY_CONTRACT_PATH}.",
        )
        assertAllowedMissingDependencyPrefixFailure(
            "null",
            "Missing required contract property allowedMissingDependencyPrefixes in ${DistributionContractPaths.RUNTIME_MODULE_DISCOVERY_CONTRACT_PATH}.",
        )
        assertAllowedMissingDependencyPrefixFailure(
            "\"org.jspecify.annotations.\"",
            "Expected JSON array contract property allowedMissingDependencyPrefixes in ${DistributionContractPaths.RUNTIME_MODULE_DISCOVERY_CONTRACT_PATH}.",
        )
        assertAllowedMissingDependencyPrefixFailure(
            "[\"org.apache.logging.log4j.\", \"org.apache.logging.log4j.\"]",
            "Duplicate contract list element org.apache.logging.log4j. in allowedMissingDependencyPrefixes from ${DistributionContractPaths.RUNTIME_MODULE_DISCOVERY_CONTRACT_PATH}.",
        )
        assertAllowedMissingDependencyPrefixFailure(
            "[\"org.apache.logging.log4j.\", \"   \"]",
            "Expected JSON string elements in contract property allowedMissingDependencyPrefixes in ${DistributionContractPaths.RUNTIME_MODULE_DISCOVERY_CONTRACT_PATH}.",
        )
        assertAllowedMissingDependencyPrefixFailure(
            "[]",
            "Contract property allowedMissingDependencyPrefixes must not be empty in ${DistributionContractPaths.RUNTIME_MODULE_DISCOVERY_CONTRACT_PATH}.",
        )
    }

    private fun assertAllowedMissingDependencyPrefixFailure(
        fieldJson: String?,
        expectedMessage: String,
    ) {
        val repositoryRoot = Files.createTempDirectory("distribution-contract-reader-runtime-module")
        try {
            DistributionContractReaderTestSupport.writeContractResource(
                repositoryRoot,
                "contract-schema-keys.json",
                DistributionContractReaderTestSupport.contractSchemaKeysJson(),
            )
            DistributionContractReaderTestSupport.writeContractResource(
                repositoryRoot,
                "runtime-module-discovery-contract.json",
                DistributionContractReaderTestSupport.runtimeModuleDiscoveryContractJson(
                    "allowedMissingDependencyPrefixes",
                    fieldJson,
                ),
            )
            val exception =
                assertFailsWith<IllegalStateException> {
                    DistributionContractReader.allowedRuntimeModuleMissingDependencyPrefixes(
                        repositoryRoot,
                    )
                }
            assertEquals(expectedMessage, exception.message)
        } finally {
            DistributionContractReaderTestSupport.deleteTree(repositoryRoot)
        }
    }
}
