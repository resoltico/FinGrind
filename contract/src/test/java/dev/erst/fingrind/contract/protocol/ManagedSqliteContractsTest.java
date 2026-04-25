package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Coverage tests for the managed-SQLite contract loader. */
class ManagedSqliteContractsTest {
  @Test
  void current_matchesTheManagedRuntimePins() {
    ManagedSqliteContract current = ManagedSqliteContracts.current();

    assertEquals("3.53.0", current.requiredMinimumSqliteVersion());
    assertEquals("2.3.3", current.requiredSqlite3mcVersion());
  }

  @Test
  void loadFromResource_rejectsMissingAndBlankValues() {
    assertThrows(
        IllegalStateException.class,
        () -> ManagedSqliteContracts.loadFromResource(null, "/missing-managed-sqlite.json"));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ManagedSqliteContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "requiredMinimumSqliteVersion": "3.53.0",
                          "requiredSqlite3mcVersion": " "
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/blank-managed-sqlite.json"));

    assertEquals(
        "requiredSqlite3mcVersion must be a non-blank JSON string.", exception.getMessage());
  }

  @Test
  void loadFromResource_returnsTypedContract() {
    ManagedSqliteContract contract =
        ManagedSqliteContracts.loadFromResource(
            new ByteArrayInputStream(
                """
                {
                  "requiredMinimumSqliteVersion": "3.53.0",
                  "requiredSqlite3mcVersion": "2.3.3"
                }
                """
                    .getBytes(StandardCharsets.UTF_8)),
            "/managed-sqlite-contract.json");

    assertEquals("3.53.0", contract.requiredMinimumSqliteVersion());
    assertEquals("2.3.3", contract.requiredSqlite3mcVersion());
  }
}
