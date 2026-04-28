package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Coverage tests for the managed-SQLite contract loader. */
class ManagedSqliteContractsTest {
  @Test
  void current_matchesTheManagedRuntimePins() {
    ManagedSqliteContract current = ManagedSqliteContracts.current();

    assertEquals("3.53.0", current.requiredMinimumSqliteVersion());
    assertEquals("2.3.3", current.requiredSqlite3mcVersion());
    assertEquals(
        "2026-04-09 11:41:38 4525003a53a7fc63ca75c59b22c79608659ca12f0131f52c18637f829977f20b",
        current.requiredSqliteSourceId());
    assertEquals(
        List.of("THREADSAFE=1", "OMIT_LOAD_EXTENSION", "TEMP_STORE=3", "SECURE_DELETE"),
        current.requiredCompileOptions());
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
                          "requiredSqlite3mcVersion": " ",
                          "requiredSqliteSourceId": "source-id",
                          "requiredCompileOptions": ["THREADSAFE=1"]
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
                  "requiredSqlite3mcVersion": "2.3.3",
                  "requiredSqliteSourceId": "source-id",
                  "requiredCompileOptions": ["THREADSAFE=1", "SECURE_DELETE"]
                }
                """
                    .getBytes(StandardCharsets.UTF_8)),
            "/managed-sqlite-contract.json");

    assertEquals("3.53.0", contract.requiredMinimumSqliteVersion());
    assertEquals("2.3.3", contract.requiredSqlite3mcVersion());
    assertEquals("source-id", contract.requiredSqliteSourceId());
    assertEquals(List.of("THREADSAFE=1", "SECURE_DELETE"), contract.requiredCompileOptions());
  }

  @Test
  void loadFromResource_rejectsBlankSourceId() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ManagedSqliteContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "requiredMinimumSqliteVersion": "3.53.0",
                          "requiredSqlite3mcVersion": "2.3.3",
                          "requiredSqliteSourceId": " ",
                          "requiredCompileOptions": ["THREADSAFE=1"]
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/blank-managed-sqlite-source-id.json"));

    assertEquals("requiredSqliteSourceId must be a non-blank JSON string.", exception.getMessage());
  }

  @Test
  void loadFromResource_rejectsEmptyCompileOptions() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ManagedSqliteContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "requiredMinimumSqliteVersion": "3.53.0",
                          "requiredSqlite3mcVersion": "2.3.3",
                          "requiredSqliteSourceId": "source-id",
                          "requiredCompileOptions": []
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/empty-managed-sqlite-compile-options.json"));

    assertEquals("requiredCompileOptions must not be empty.", exception.getMessage());
  }

  @Test
  void loadFromResource_rejectsDuplicateCompileOptions() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ManagedSqliteContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "requiredMinimumSqliteVersion": "3.53.0",
                          "requiredSqlite3mcVersion": "2.3.3",
                          "requiredSqliteSourceId": "source-id",
                          "requiredCompileOptions": ["THREADSAFE=1", "THREADSAFE=1"]
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/duplicate-managed-sqlite-compile-options.json"));

    assertEquals("requiredCompileOptions must not contain duplicates.", exception.getMessage());
  }
}
