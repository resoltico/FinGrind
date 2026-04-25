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
    assertEquals(
        "unsupportedPublicCliBundleTargets",
        current.publicDistribution().unsupportedPublicCliBundleTargets());
    assertEquals(
        "requiredMinimumSqliteVersion", current.managedSqlite().requiredMinimumSqliteVersion());
    assertEquals("bundleTargets", current.bundleLayout().bundleTargets());
    assertEquals("PRINT_PLAN_TEMPLATE", current.operationIds().printPlanTemplate());
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
