package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/** Coverage tests for the managed-SQLite contract loader. */
class ManagedSqliteContractsTest {
  @Test
  void current_matchesTheManagedRuntimePins() {
    ManagedSqliteContract current = ManagedSqliteContracts.current();

    assertEquals("3.53.3", current.requiredMinimumSqliteVersion());
    assertEquals("2.3.6", current.requiredSqlite3mcVersion());
    assertEquals(
        "2026-06-26 20:14:12 d4c0e51e4aeb96955b99185ab9cde75c339e2c29c3f3f12428d364a10d782c62",
        current.requiredSqliteSourceId());
    assertEquals("sqlite3mc-amalgamation-2.3.6-sqlite-3530300", current.requiredSourcePackageId());
    assertEquals(
        Map.of(
            "README.md", "c10718d44aea623eae8e7d933f0ec51f2ec0e69e76157a413373266288f2b0cf",
            "shell3mc_amalgamation.c",
                "93132925600943d5711faf391e70b40edd1773d88ac04287d7243af427763eb1",
            "sqlite3.h", "8444bae1916728ffb4a8c7b00434616b12cf3df813b3dd52127849a7c0387d9c",
            "sqlite3ext.h", "1b7a0ee438bb5c2896d0609c537e917d8057b3340f6ad004d2de44f03e3d3cca",
            "sqlite3mc_amalgamation.c",
                "33725ff1edc60763060747a3491282befee52ae9f217c2cc6e0e1b2f86d4dec2",
            "sqlite3mc_amalgamation.h",
                "f74b9a4c92bc1e22fc27c57c052d96b5f55699c3c336ceb4cedad2b477e4cb93"),
        current.vendoredReleaseFiles());
    assertEquals(
        List.of("-fstack-protector-strong"), current.nativeHardening().unixCompilerFlags());
    assertEquals(
        List.of("-Wl,-z,relro", "-Wl,-z,now", "-Wl,-z,noexecstack"),
        current.nativeHardening().linuxLinkerFlags());
    assertEquals(List.of(), current.nativeHardening().macosLinkerFlags());
    assertEquals(List.of("/GS"), current.nativeHardening().windowsCompilerFlags());
    assertEquals(
        List.of("/DYNAMICBASE", "/NXCOMPAT", "/GUARD:CF"),
        current.nativeHardening().windowsLinkerFlags());
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
                              "requiredMinimumSqliteVersion": "3.53.3",
                              "requiredSqlite3mcVersion": "2.3.6",
                              "requiredSqliteSourceId": "source-id",
                              "requiredSourcePackageId": "sqlite3mc-amalgamation-test",
                              "vendoredReleaseFiles": {"sqlite3mc_amalgamation.c": "sha3-a"},
                              "nativeHardening": {
                                "unixCompilerFlags": ["-fstack-protector-strong"],
                                "linuxLinkerFlags": ["-Wl,-z,relro"],
                                "macosLinkerFlags": [],
                                "windowsCompilerFlags": ["/GS"],
                                "windowsLinkerFlags": ["/NXCOMPAT"]
                              },
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
                          "requiredMinimumSqliteVersion": "3.53.3",
                          "requiredSqlite3mcVersion": " ",
                          "requiredSqliteSourceId": "source-id",
                          "requiredSourcePackageId": "sqlite3mc-amalgamation-test",
                          "vendoredReleaseFiles": {"sqlite3mc_amalgamation.c": "sha3-a"},
                          "nativeHardening": {
                            "unixCompilerFlags": ["-fstack-protector-strong"],
                            "linuxLinkerFlags": ["-Wl,-z,relro"],
                            "macosLinkerFlags": [],
                            "windowsCompilerFlags": ["/GS"],
                            "windowsLinkerFlags": ["/NXCOMPAT"]
                          },
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
                  "requiredMinimumSqliteVersion": "3.53.3",
                  "requiredSqlite3mcVersion": "2.3.6",
                  "requiredSqliteSourceId": "source-id",
                  "requiredSourcePackageId": "sqlite3mc-amalgamation-test",
                  "vendoredReleaseFiles": {"sqlite3mc_amalgamation.c": "sha3-a"},
                  "nativeHardening": {
                    "unixCompilerFlags": ["-fstack-protector-strong"],
                    "linuxLinkerFlags": ["-Wl,-z,relro"],
                    "macosLinkerFlags": [],
                    "windowsCompilerFlags": ["/GS"],
                    "windowsLinkerFlags": ["/NXCOMPAT"]
                  },
                  "requiredCompileOptions": ["THREADSAFE=1", "SECURE_DELETE"],
                  "forbiddenCompileOptions": ["USE_URI"],
                  "requiresSecureMemorySupport": true
                }
                """
                    .getBytes(StandardCharsets.UTF_8)),
            "/managed-sqlite-contract.json");

    assertEquals("3.53.3", contract.requiredMinimumSqliteVersion());
    assertEquals("2.3.6", contract.requiredSqlite3mcVersion());
    assertEquals("source-id", contract.requiredSqliteSourceId());
    assertEquals("sqlite3mc-amalgamation-test", contract.requiredSourcePackageId());
    assertEquals("sha3-a", contract.vendoredReleaseFiles().get("sqlite3mc_amalgamation.c"));
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
                          "requiredMinimumSqliteVersion": "3.53.3",
                          "requiredSqlite3mcVersion": "2.3.6",
                          "requiredSqliteSourceId": " ",
                          "requiredSourcePackageId": "sqlite3mc-amalgamation-test",
                          "vendoredReleaseFiles": {"sqlite3mc_amalgamation.c": "sha3-a"},
                          "nativeHardening": {
                            "unixCompilerFlags": ["-fstack-protector-strong"],
                            "linuxLinkerFlags": ["-Wl,-z,relro"],
                            "macosLinkerFlags": [],
                            "windowsCompilerFlags": ["/GS"],
                            "windowsLinkerFlags": ["/NXCOMPAT"]
                          },
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
                          "requiredMinimumSqliteVersion": "3.53.3",
                          "requiredSqlite3mcVersion": "2.3.6",
                          "requiredSqliteSourceId": "source-id",
                          "requiredSourcePackageId": "sqlite3mc-amalgamation-test",
                          "vendoredReleaseFiles": {"sqlite3mc_amalgamation.c": "sha3-a"},
                          "nativeHardening": {
                            "unixCompilerFlags": ["-fstack-protector-strong"],
                            "linuxLinkerFlags": ["-Wl,-z,relro"],
                            "macosLinkerFlags": [],
                            "windowsCompilerFlags": ["/GS"],
                            "windowsLinkerFlags": ["/NXCOMPAT"]
                          },
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
  void loadFromResource_rejectsEmptyVendoredReleaseFiles() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ManagedSqliteContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "requiredMinimumSqliteVersion": "3.53.3",
                          "requiredSqlite3mcVersion": "2.3.6",
                          "requiredSqliteSourceId": "source-id",
                          "requiredSourcePackageId": "sqlite3mc-amalgamation-test",
                          "vendoredReleaseFiles": {},
                          "nativeHardening": {
                            "unixCompilerFlags": ["-fstack-protector-strong"],
                            "linuxLinkerFlags": ["-Wl,-z,relro"],
                            "macosLinkerFlags": [],
                            "windowsCompilerFlags": ["/GS"],
                            "windowsLinkerFlags": ["/NXCOMPAT"]
                          },
                          "requiredCompileOptions": ["THREADSAFE=1"],
                          "forbiddenCompileOptions": ["USE_URI"],
                          "requiresSecureMemorySupport": true
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/empty-managed-sqlite-vendored-files.json"));

    assertEquals("vendoredReleaseFiles must not be empty.", exception.getMessage());
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
                          "requiredMinimumSqliteVersion": "3.53.3",
                          "requiredSqlite3mcVersion": "2.3.6",
                          "requiredSqliteSourceId": "source-id",
                          "requiredSourcePackageId": "sqlite3mc-amalgamation-test",
                          "vendoredReleaseFiles": {"sqlite3mc_amalgamation.c": "sha3-a"},
                          "nativeHardening": {
                            "unixCompilerFlags": ["-fstack-protector-strong"],
                            "linuxLinkerFlags": ["-Wl,-z,relro"],
                            "macosLinkerFlags": [],
                            "windowsCompilerFlags": ["/GS"],
                            "windowsLinkerFlags": ["/NXCOMPAT"]
                          },
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
                          "requiredMinimumSqliteVersion": "3.53.3",
                          "requiredSqlite3mcVersion": "2.3.6",
                          "requiredSqliteSourceId": "source-id",
                          "requiredSourcePackageId": "sqlite3mc-amalgamation-test",
                          "vendoredReleaseFiles": {"sqlite3mc_amalgamation.c": "sha3-a"},
                          "nativeHardening": {
                            "unixCompilerFlags": ["-fstack-protector-strong"],
                            "linuxLinkerFlags": ["-Wl,-z,relro"],
                            "macosLinkerFlags": [],
                            "windowsCompilerFlags": ["/GS"],
                            "windowsLinkerFlags": ["/NXCOMPAT"]
                          },
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

  @Test
  void loadFromResource_rejectsInvalidVendoredReleasePath() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ManagedSqliteContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "requiredMinimumSqliteVersion": "3.53.3",
                          "requiredSqlite3mcVersion": "2.3.6",
                          "requiredSqliteSourceId": "source-id",
                          "requiredSourcePackageId": "sqlite3mc-amalgamation-test",
                          "vendoredReleaseFiles": {"../sqlite3mc_amalgamation.c": "sha3-a"},
                          "nativeHardening": {
                            "unixCompilerFlags": ["-fstack-protector-strong"],
                            "linuxLinkerFlags": ["-Wl,-z,relro"],
                            "macosLinkerFlags": [],
                            "windowsCompilerFlags": ["/GS"],
                            "windowsLinkerFlags": ["/NXCOMPAT"]
                          },
                          "requiredCompileOptions": ["THREADSAFE=1"],
                          "forbiddenCompileOptions": ["USE_URI"],
                          "requiresSecureMemorySupport": true
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/invalid-managed-sqlite-vendored-path.json"));

    assertEquals(
        "vendoredReleaseFiles keys must be normalized relative paths.", exception.getMessage());
  }

  @Test
  void loadFromResource_rejectsDuplicateHardeningFlags() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ManagedSqliteContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "requiredMinimumSqliteVersion": "3.53.3",
                          "requiredSqlite3mcVersion": "2.3.6",
                          "requiredSqliteSourceId": "source-id",
                          "requiredSourcePackageId": "sqlite3mc-amalgamation-test",
                          "vendoredReleaseFiles": {"sqlite3mc_amalgamation.c": "sha3-a"},
                          "nativeHardening": {
                            "unixCompilerFlags": ["-fstack-protector-strong"],
                            "linuxLinkerFlags": ["-Wl,-z,relro", "-Wl,-z,relro"],
                            "macosLinkerFlags": [],
                            "windowsCompilerFlags": ["/GS"],
                            "windowsLinkerFlags": ["/NXCOMPAT"]
                          },
                          "requiredCompileOptions": ["THREADSAFE=1"],
                          "forbiddenCompileOptions": ["USE_URI"],
                          "requiresSecureMemorySupport": true
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/duplicate-managed-sqlite-hardening-flags.json"));

    assertEquals("linuxLinkerFlags must not contain duplicates.", exception.getMessage());
  }

  @Test
  void managedSqliteContract_rejectsEmptyVendoredReleaseFiles() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ManagedSqliteContract(
                    "3.53.3",
                    "2.3.6",
                    "source-id",
                    "sqlite3mc-amalgamation-test",
                    Map.of(),
                    validNativeHardening(),
                    List.of("THREADSAFE=1"),
                    List.of("USE_URI"),
                    true));

    assertEquals("vendoredReleaseFiles must not be empty.", exception.getMessage());
  }

  @Test
  void managedSqliteContract_rejectsAbsoluteVendoredReleasePath() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ManagedSqliteContract(
                    "3.53.3",
                    "2.3.6",
                    "source-id",
                    "sqlite3mc-amalgamation-test",
                    Map.of("/sqlite3mc_amalgamation.c", "sha3-a"),
                    validNativeHardening(),
                    List.of("THREADSAFE=1"),
                    List.of("USE_URI"),
                    true));

    assertEquals(
        "vendoredReleaseFiles keys must be normalized relative paths.", exception.getMessage());
  }

  private static ManagedSqliteContract.NativeHardeningContract validNativeHardening() {
    return new ManagedSqliteContract.NativeHardeningContract(
        List.of("-fstack-protector-strong"),
        List.of("-Wl,-z,relro"),
        List.of(),
        List.of("/GS"),
        List.of("/NXCOMPAT"));
  }

  @Test
  void managedSqliteContract_rejectsDuplicateVendoredReleasePaths() {
    Map<String, String> duplicatePathMap =
        new ConcurrentHashMap<>() {
          {
            put("sqlite3mc_amalgamation.c", "sha3-a");
          }

          @Override
          public Set<Entry<String, String>> entrySet() {
            Set<Entry<String, String>> entries = new java.util.LinkedHashSet<>();
            entries.add(new SimpleEntry<>("sqlite3mc_amalgamation.c", "sha3-a"));
            entries.add(new SimpleEntry<>("sqlite3mc_amalgamation.c", "sha3-b"));
            return entries;
          }
        };

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ManagedSqliteContract(
                    "3.53.3",
                    "2.3.6",
                    "source-id",
                    "sqlite3mc-amalgamation-test",
                    duplicatePathMap,
                    validNativeHardening(),
                    List.of("THREADSAFE=1"),
                    List.of("USE_URI"),
                    true));

    assertEquals("vendoredReleaseFiles must not contain duplicates.", exception.getMessage());
  }
}
