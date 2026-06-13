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

    assertEquals("3.53.2", current.requiredMinimumSqliteVersion());
    assertEquals("2.3.5", current.requiredSqlite3mcVersion());
    assertEquals(
        "2026-06-03 19:12:13 d6e03d8c777cfa2d35e3b60d8ec3e0187f3e9f99d8e2ee9cac695fd6fcdf1a24",
        current.requiredSqliteSourceId());
    assertEquals("sqlite3mc-amalgamation-2.3.5-sqlite-3530200", current.requiredSourcePackageId());
    assertEquals(
        Map.of(
            "readme-amalgamation.md",
                "7db1b43f361fb8a976e22205e0f257191a4c56fce1cd21d0ca6a080b105f32d7",
            "shell3mc_amalgamation.c",
                "4e00bda46a25c67609451af51199e8fe7210334b1befc01ac2cc1277c514eeab",
            "sqlite3ext.h", "1b7a0ee438bb5c2896d0609c537e917d8057b3340f6ad004d2de44f03e3d3cca",
            "sqlite3mc_amalgamation.c",
                "330ff26dea1db7e73ffa8a8cebbdf09bf63a2cf731b94b9735c75156618b0329",
            "sqlite3mc_amalgamation.h",
                "c3ec4147cfd8d52c6747e3a1a78851f5e0d7101ab5cab3f155a2987f77b5dbc2"),
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
                              "requiredMinimumSqliteVersion": "3.53.2",
                              "requiredSqlite3mcVersion": "2.3.5",
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
                          "requiredMinimumSqliteVersion": "3.53.2",
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
                  "requiredMinimumSqliteVersion": "3.53.2",
                  "requiredSqlite3mcVersion": "2.3.5",
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

    assertEquals("3.53.2", contract.requiredMinimumSqliteVersion());
    assertEquals("2.3.5", contract.requiredSqlite3mcVersion());
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
                          "requiredMinimumSqliteVersion": "3.53.2",
                          "requiredSqlite3mcVersion": "2.3.5",
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
                          "requiredMinimumSqliteVersion": "3.53.2",
                          "requiredSqlite3mcVersion": "2.3.5",
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
                          "requiredMinimumSqliteVersion": "3.53.2",
                          "requiredSqlite3mcVersion": "2.3.5",
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
                          "requiredMinimumSqliteVersion": "3.53.2",
                          "requiredSqlite3mcVersion": "2.3.5",
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
                          "requiredMinimumSqliteVersion": "3.53.2",
                          "requiredSqlite3mcVersion": "2.3.5",
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
                          "requiredMinimumSqliteVersion": "3.53.2",
                          "requiredSqlite3mcVersion": "2.3.5",
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
                          "requiredMinimumSqliteVersion": "3.53.2",
                          "requiredSqlite3mcVersion": "2.3.5",
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
                    "3.53.2",
                    "2.3.5",
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
                    "3.53.2",
                    "2.3.5",
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
                    "3.53.2",
                    "2.3.5",
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
