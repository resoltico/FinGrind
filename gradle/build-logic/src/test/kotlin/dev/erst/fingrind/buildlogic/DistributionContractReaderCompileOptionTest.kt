package dev.erst.fingrind.buildlogic

import kotlin.test.Test

class DistributionContractReaderCompileOptionTest {
    @Test
    fun sqliteCompileOptionContracts_failClosedOnMissingNullMalformedDuplicateBlankAndEmptyLists() {
        DistributionContractReaderTestSupport.assertCompileOptionListFailure(
            "requiredCompileOptions",
            null,
            "Missing required contract property requiredCompileOptions in ${DistributionContractReaderTestSupport.managedSqliteContractPath}.",
        ) {
            DistributionContractReader.requiredSqliteCompileOptions(it)
        }
        DistributionContractReaderTestSupport.assertCompileOptionListFailure(
            "requiredCompileOptions",
            "null",
            "Missing required contract property requiredCompileOptions in ${DistributionContractReaderTestSupport.managedSqliteContractPath}.",
        ) {
            DistributionContractReader.requiredSqliteCompileOptions(it)
        }
        DistributionContractReaderTestSupport.assertCompileOptionListFailure(
            "requiredCompileOptions",
            "\"THREADSAFE=1\"",
            "Expected JSON array contract property requiredCompileOptions in ${DistributionContractReaderTestSupport.managedSqliteContractPath}.",
        ) {
            DistributionContractReader.requiredSqliteCompileOptions(it)
        }
        DistributionContractReaderTestSupport.assertCompileOptionListFailure(
            "requiredCompileOptions",
            "[\"THREADSAFE=1\", \"THREADSAFE=1\"]",
            "Duplicate contract list element THREADSAFE=1 in requiredCompileOptions from ${DistributionContractReaderTestSupport.managedSqliteContractPath}.",
        ) {
            DistributionContractReader.requiredSqliteCompileOptions(it)
        }
        DistributionContractReaderTestSupport.assertCompileOptionListFailure(
            "requiredCompileOptions",
            "[\"THREADSAFE=1\", \"   \"]",
            "Expected JSON string elements in contract property requiredCompileOptions in ${DistributionContractReaderTestSupport.managedSqliteContractPath}.",
        ) {
            DistributionContractReader.requiredSqliteCompileOptions(it)
        }
        DistributionContractReaderTestSupport.assertCompileOptionListFailure(
            "requiredCompileOptions",
            "[]",
            "Contract property requiredCompileOptions must not be empty in ${DistributionContractReaderTestSupport.managedSqliteContractPath}.",
        ) {
            DistributionContractReader.requiredSqliteCompileOptions(it)
        }
        DistributionContractReaderTestSupport.assertCompileOptionListFailure(
            "forbiddenCompileOptions",
            null,
            "Missing required contract property forbiddenCompileOptions in ${DistributionContractReaderTestSupport.managedSqliteContractPath}.",
        ) {
            DistributionContractReader.forbiddenSqliteCompileOptions(it)
        }
        DistributionContractReaderTestSupport.assertCompileOptionListFailure(
            "forbiddenCompileOptions",
            "null",
            "Missing required contract property forbiddenCompileOptions in ${DistributionContractReaderTestSupport.managedSqliteContractPath}.",
        ) {
            DistributionContractReader.forbiddenSqliteCompileOptions(it)
        }
        DistributionContractReaderTestSupport.assertCompileOptionListFailure(
            "forbiddenCompileOptions",
            "\"USE_URI\"",
            "Expected JSON array contract property forbiddenCompileOptions in ${DistributionContractReaderTestSupport.managedSqliteContractPath}.",
        ) {
            DistributionContractReader.forbiddenSqliteCompileOptions(it)
        }
        DistributionContractReaderTestSupport.assertCompileOptionListFailure(
            "forbiddenCompileOptions",
            "[\"USE_URI\", \"USE_URI\"]",
            "Duplicate contract list element USE_URI in forbiddenCompileOptions from ${DistributionContractReaderTestSupport.managedSqliteContractPath}.",
        ) {
            DistributionContractReader.forbiddenSqliteCompileOptions(it)
        }
        DistributionContractReaderTestSupport.assertCompileOptionListFailure(
            "forbiddenCompileOptions",
            "[\"USE_URI\", \"   \"]",
            "Expected JSON string elements in contract property forbiddenCompileOptions in ${DistributionContractReaderTestSupport.managedSqliteContractPath}.",
        ) {
            DistributionContractReader.forbiddenSqliteCompileOptions(it)
        }
        DistributionContractReaderTestSupport.assertCompileOptionListFailure(
            "forbiddenCompileOptions",
            "[]",
            "Contract property forbiddenCompileOptions must not be empty in ${DistributionContractReaderTestSupport.managedSqliteContractPath}.",
        ) {
            DistributionContractReader.forbiddenSqliteCompileOptions(it)
        }
    }
}
