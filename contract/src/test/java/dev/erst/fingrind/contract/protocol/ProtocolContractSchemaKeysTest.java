package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Regression tests for the shared schema-key owner used across protocol contract resources. */
class ProtocolContractSchemaKeysTest {
  @Test
  void current_exposesCanonicalRuntimeDistributionAndOperationIdKeys() {
    ProtocolContractSchemaKeys current = ProtocolContractSchemaKeys.current();

    assertEquals(
        "directJavaRuntimeDistribution", current.runtimeSurface().directJavaRuntimeDistribution());
    assertEquals("cipher", current.protectedBookFormat().cipher());
    assertEquals(
        "unsupportedPublicCliBundleTargets",
        current.publicDistribution().unsupportedPublicCliBundleTargets());
    assertEquals(
        "requiredMinimumSqliteVersion", current.managedSqlite().requiredMinimumSqliteVersion());
    assertEquals("bundleTargets", current.bundleLayout().bundleTargets());
    assertEquals("VERSION", current.operationIds().version());
    assertEquals("PRINT_PLAN_TEMPLATE", current.operationIds().printPlanTemplate());
    assertEquals("GENERATE_BOOK_KEY_FILE", current.operationIds().generateBookKeyFile());
    assertEquals("BACKUP_BOOK", current.operationIds().backupBook());
    assertEquals("RESTORE_BOOK", current.operationIds().restoreBook());
    assertEquals("INSPECT_REKEY_ROLLBACK", current.operationIds().inspectRekeyRollback());
    assertEquals("DELETE_REKEY_ROLLBACK", current.operationIds().deleteRekeyRollback());
    assertEquals("RESTORE_REKEY_ROLLBACK", current.operationIds().restoreRekeyRollback());
    assertEquals("TRANSFER_PERIOD_RESULT", current.operationIds().transferPeriodResult());
    assertEquals("LIST_POSTINGS", current.operationIds().listPostings());
    assertEquals("FINANCIAL_POSITION", current.operationIds().financialPosition());
    assertEquals("INCOME_STATEMENT", current.operationIds().incomeStatement());
    assertEquals("CHANGES_IN_EQUITY", current.operationIds().changesInEquity());
    assertEquals("EXECUTE_PLAN", current.operationIds().executePlan());
    assertEquals("POST_ENTRY", current.operationIds().postEntry());
  }

  @Test
  void loadFromResource_rejectsMissingAndNonObjectSchemaSections() {
    IllegalArgumentException missingSection =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ProtocolContractSchemaKeys.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "runtimeSurface": {},
                          "protectedBookFormat": {},
                          "managedSqlite": {},
                          "bundleLayout": {},
                          "operationIdContract": {}
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/missing-public-distribution.json"));

    assertEquals(
        "publicDistribution must be one JSON object of schema keys.", missingSection.getMessage());

    IllegalArgumentException wrongSectionType =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ProtocolContractSchemaKeys.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "runtimeSurface": 1,
                          "protectedBookFormat": {},
                          "managedSqlite": {},
                          "bundleLayout": {},
                          "publicDistribution": {},
                          "operationIdContract": {}
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/wrong-runtime-surface.json"));

    assertEquals(
        "runtimeSurface must be one JSON object of schema keys.", wrongSectionType.getMessage());
  }
}
