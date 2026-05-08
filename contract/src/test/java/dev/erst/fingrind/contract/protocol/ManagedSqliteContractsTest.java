package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Coverage tests for the managed-SQLite contract loader. */
class ManagedSqliteContractsTest {
  @Test
  void current_matchesTheManagedRuntimePins() {
    ManagedSqliteContract current = ManagedSqliteContracts.current();

    assertEquals("3.53.1", current.requiredMinimumSqliteVersion());
    assertEquals("2.3.4", current.requiredSqlite3mcVersion());
    assertEquals(
        "2026-05-05 10:34:17 c88b22011a54b4f6fbd149e9f8e4de77658ce58143a1af0e3785e4e6475127e9",
        current.requiredSqliteSourceId());
    assertEquals(
        List.of("THREADSAFE=1", "OMIT_LOAD_EXTENSION", "TEMP_STORE=3", "SECURE_DELETE"),
        current.requiredCompileOptions());
    assertEquals(List.of("USE_URI"), current.forbiddenCompileOptions());
    assertTrue(current.requiresSecureMemorySupport());
  }

  @Test
  void loadFromResource_rejectsMissingAndBlankValues() {
    assertThrows(
        IllegalStateException.class,
        () -> ManagedSqliteContracts.loadFromResource(null, "/missing-managed-sqlite.json"));
    assertEquals(
        "forbiddenCompileOptions must be a JSON array of strings.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ManagedSqliteContracts.loadFromResource(
                        new ByteArrayInputStream(
                            """
                            {
                              "requiredMinimumSqliteVersion": "3.53.1",
                              "requiredSqlite3mcVersion": "2.3.4",
                              "requiredSqliteSourceId": "source-id",
                              "requiredCompileOptions": ["THREADSAFE=1"],
                              "requiresSecureMemorySupport": true
                            }
                            """
                                .getBytes(StandardCharsets.UTF_8)),
                        "/missing-managed-sqlite-forbidden-options.json"))
            .getMessage());

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ManagedSqliteContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "requiredMinimumSqliteVersion": "3.53.1",
                          "requiredSqlite3mcVersion": " ",
                          "requiredSqliteSourceId": "source-id",
                          "requiredCompileOptions": ["THREADSAFE=1"],
                          "forbiddenCompileOptions": ["USE_URI"],
                          "requiresSecureMemorySupport": true
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
                  "requiredMinimumSqliteVersion": "3.53.1",
                  "requiredSqlite3mcVersion": "2.3.4",
                  "requiredSqliteSourceId": "source-id",
                  "requiredCompileOptions": ["THREADSAFE=1", "SECURE_DELETE"],
                  "forbiddenCompileOptions": ["USE_URI"],
                  "requiresSecureMemorySupport": true
                }
                """
                    .getBytes(StandardCharsets.UTF_8)),
            "/managed-sqlite-contract.json");

    assertEquals("3.53.1", contract.requiredMinimumSqliteVersion());
    assertEquals("2.3.4", contract.requiredSqlite3mcVersion());
    assertEquals("source-id", contract.requiredSqliteSourceId());
    assertEquals(List.of("THREADSAFE=1", "SECURE_DELETE"), contract.requiredCompileOptions());
    assertEquals(List.of("USE_URI"), contract.forbiddenCompileOptions());
    assertTrue(contract.requiresSecureMemorySupport());
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
                          "requiredMinimumSqliteVersion": "3.53.1",
                          "requiredSqlite3mcVersion": "2.3.4",
                          "requiredSqliteSourceId": " ",
                          "requiredCompileOptions": ["THREADSAFE=1"],
                          "forbiddenCompileOptions": ["USE_URI"],
                          "requiresSecureMemorySupport": true
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
                          "requiredMinimumSqliteVersion": "3.53.1",
                          "requiredSqlite3mcVersion": "2.3.4",
                          "requiredSqliteSourceId": "source-id",
                          "requiredCompileOptions": [],
                          "forbiddenCompileOptions": ["USE_URI"],
                          "requiresSecureMemorySupport": true
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
                          "requiredMinimumSqliteVersion": "3.53.1",
                          "requiredSqlite3mcVersion": "2.3.4",
                          "requiredSqliteSourceId": "source-id",
                          "requiredCompileOptions": ["THREADSAFE=1", "THREADSAFE=1"],
                          "forbiddenCompileOptions": ["USE_URI"],
                          "requiresSecureMemorySupport": true
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/duplicate-managed-sqlite-compile-options.json"));

    assertEquals("requiredCompileOptions must not contain duplicates.", exception.getMessage());
  }

  @Test
  void loadFromResource_rejectsOverlappingCompileOptions() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ManagedSqliteContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "requiredMinimumSqliteVersion": "3.53.1",
                          "requiredSqlite3mcVersion": "2.3.4",
                          "requiredSqliteSourceId": "source-id",
                          "requiredCompileOptions": ["THREADSAFE=1", "USE_URI"],
                          "forbiddenCompileOptions": ["USE_URI"],
                          "requiresSecureMemorySupport": true
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/overlapping-managed-sqlite-compile-options.json"));

    assertEquals(
        "requiredCompileOptions and forbiddenCompileOptions must not overlap.",
        exception.getMessage());
  }
}
