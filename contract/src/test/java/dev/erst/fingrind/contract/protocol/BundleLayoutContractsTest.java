package dev.erst.fingrind.contract.protocol;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Coverage tests for the canonical per-target bundle-layout contract loader. */
class BundleLayoutContractsTest {
  @Test
  void current_coversEveryPublicCliBundleTarget() {
    BundleLayoutContract current = BundleLayoutContracts.current();

    assertEquals(EnumSet.allOf(PublicCliBundleTarget.class), current.bundleTargets().keySet());
    assertEquals(
        EnumSet.allOf(PublicCliBundleTarget.class),
        ProtocolCatalogFacts.bundlePublicationContract().bundleTargets().keySet());
    assertEquals(current, ProtocolCatalogFacts.bundleLayoutContract());
    assertEquals(
        "zip", current.bundleTarget(PublicCliBundleTarget.WINDOWS_AARCH64).archiveFormat());
    assertEquals(
        "sqlite3.dll",
        current.bundleTarget(PublicCliBundleTarget.WINDOWS_AARCH64).sqliteLibraryFileName());
    assertEquals(
        "glibc 2.34+ Linux x86_64",
        current.bundleTarget(PublicCliBundleTarget.LINUX_X86_64).compatibilityLabel());
    assertEquals(
        "2.34",
        current
            .bundleTarget(PublicCliBundleTarget.LINUX_X86_64)
            .minimumGlibcVersion()
            .orElseThrow());
    assertEquals(
        "rockylinux:9@sha256:d7be1c094cc5845ee815d4632fe377514ee6ebcf8efaed6892889657e5ddaaa6",
        current
            .bundleTarget(PublicCliBundleTarget.LINUX_X86_64)
            .compatibilitySmokeContainerImage()
            .orElseThrow());
    assertEquals(
        List.of(
            PublicCliBundleTarget.MACOS_AARCH64,
            PublicCliBundleTarget.MACOS_X86_64,
            PublicCliBundleTarget.LINUX_X86_64,
            PublicCliBundleTarget.LINUX_AARCH64,
            PublicCliBundleTarget.WINDOWS_X86_64),
        current.supportedPublicCliBundleTargets());
    assertEquals(
        List.of(PublicCliBundleTarget.WINDOWS_AARCH64),
        current.unsupportedPublicCliBundleTargets());
  }

  @Test
  void loadFromResource_usesCanonicalPublicationContract() {
    BundleLayoutContract loaded =
        BundleLayoutContracts.loadFromResource(
            BundleLayoutContractsTest.class.getResourceAsStream(
                "/dev/erst/fingrind/contract/protocol/bundle-layout-contract.json"),
            "/dev/erst/fingrind/contract/protocol/bundle-layout-contract.json");

    assertEquals(BundleLayoutContracts.current(), loaded);
  }

  @Test
  void bundleLayoutContract_rejectsPartialTargetCoverage() {
    IllegalArgumentException partialCoverage =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BundleLayoutContract(
                    Map.of(
                        PublicCliBundleTarget.LINUX_X86_64,
                        new BundleLayoutContract.BundleTarget(
                            "linux",
                            "x86_64",
                            "tar.gz",
                            "bin/fingrind",
                            "./bin/fingrind",
                            "libsqlite3.so.0",
                            "glibc 2.34+ Linux x86_64",
                            Optional.of("2.34"),
                            Optional.of("rockylinux:9@sha256:floor-proof"),
                            new BundleLayoutContract.PublicBundlePublication(
                                PublicBundlePublicationStatus.PUBLISHED,
                                Optional.of("ubuntu-24.04"),
                                Optional.of("Linux"),
                                Optional.of("x86_64"))))));

    assertEquals(
        "bundleTargets must cover every public CLI bundle target: [macos-aarch64, macos-x86_64, linux-aarch64, windows-x86_64, windows-aarch64]",
        partialCoverage.getMessage());
  }

  @Test
  void requireBundleTarget_reportsMissingTargetRegistration() {
    IllegalStateException missingTarget =
        assertThrows(
            IllegalStateException.class,
            () ->
                BundleLayoutContract.requireBundleTarget(
                    Map.of(
                        PublicCliBundleTarget.LINUX_X86_64,
                        new BundleLayoutContract.BundleTarget(
                            "linux",
                            "x86_64",
                            "tar.gz",
                            "bin/fingrind",
                            "./bin/fingrind",
                            "libsqlite3.so.0",
                            "glibc 2.34+ Linux x86_64",
                            Optional.of("2.34"),
                            Optional.of("rockylinux:9@sha256:floor-proof"),
                            new BundleLayoutContract.PublicBundlePublication(
                                PublicBundlePublicationStatus.PUBLISHED,
                                Optional.of("ubuntu-24.04"),
                                Optional.of("Linux"),
                                Optional.of("x86_64")))),
                    PublicCliBundleTarget.WINDOWS_AARCH64));

    assertEquals(
        "No bundle-layout contract is registered for bundle target windows-aarch64.",
        missingTarget.getMessage());
  }

  @Test
  void loadFromResource_rejectsMissingBundleTargetsAndContradictoryEntries() {
    IllegalArgumentException missingBundleTargets =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BundleLayoutContracts.loadFromResource(
                    new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)),
                    "/missing-bundle-layout.json"));

    assertEquals("bundleTargets must be one JSON object.", missingBundleTargets.getMessage());

    IllegalArgumentException nonObjectBundleTargets =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BundleLayoutContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "bundleTargets": []
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/non-object-bundle-targets.json"));

    assertEquals("bundleTargets must be one JSON object.", nonObjectBundleTargets.getMessage());

    IllegalStateException contradictoryClassifier =
        assertThrows(
            IllegalStateException.class,
            () ->
                BundleLayoutContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "bundleTargets": {
                            "linux-x86_64": {
                              "operatingSystemId": "linux",
                              "architectureId": "aarch64",
                              "archiveFormat": "tar.gz",
                              "launcherPath": "bin/fingrind",
                              "launcherCommand": "./bin/fingrind",
                              "sqliteLibraryFileName": "libsqlite3.so.0",
                              "compatibilityLabel": "glibc 2.34+ Linux aarch64",
                              "minimumGlibcVersion": "2.34",
                              "compatibilitySmokeContainerImage": "rockylinux:9@sha256:floor-proof"
                            }
                          }
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/contradictory-bundle-layout.json"));

    assertEquals(
        "Bundle layout target linux-x86_64 must agree with linux-aarch64.",
        contradictoryClassifier.getMessage());

    IllegalArgumentException nonObjectEntry =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BundleLayoutContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "bundleTargets": {
                            "linux-x86_64": "wrong"
                          }
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/non-object-bundle-layout.json"));

    assertEquals("bundleTargets entry must be one JSON object.", nonObjectEntry.getMessage());
  }

  @Test
  void loadFromResources_rejectsMismatchedBundleTargetSets() {
    IllegalStateException mismatchedTargetSets =
        assertThrows(
            IllegalStateException.class,
            () ->
                BundleLayoutContracts.loadFromResources(
                    new ByteArrayInputStream(
                        """
                        {
                          "bundleTargets": {
                            "linux-x86_64": {
                              "operatingSystemId": "linux",
                              "architectureId": "x86_64",
                              "archiveFormat": "tar.gz",
                              "launcherPath": "bin/fingrind",
                              "launcherCommand": "./bin/fingrind",
                              "sqliteLibraryFileName": "libsqlite3.so.0",
                              "compatibilityLabel": "glibc 2.34+ Linux x86_64",
                              "minimumGlibcVersion": "2.34",
                              "compatibilitySmokeContainerImage": "rockylinux:9@sha256:floor-proof"
                            }
                          }
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/bundle-layout.json",
                    new ByteArrayInputStream(
                        """
                        {
                          "bundleTargets": {
                            "linux-x86_64": {
                              "status": "published",
                              "runnerLabel": "ubuntu-24.04",
                              "expectedRunnerOs": "Linux",
                              "expectedRunnerArch": "x86_64"
                            },
                            "windows-aarch64": {
                              "status": "not-published"
                            }
                          }
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/bundle-publication.json"));

    assertEquals(
        "bundle layout and bundle publication contracts must cover the same bundle-target set.",
        mismatchedTargetSets.getMessage());
  }

  @Test
  void bundleTarget_enforcesLinuxGlibcMetadata() {
    IllegalArgumentException missingLinuxFloor =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BundleLayoutContract.BundleTarget(
                    "linux",
                    "x86_64",
                    "tar.gz",
                    "bin/fingrind",
                    "./bin/fingrind",
                    "libsqlite3.so.0",
                    "glibc 2.34+ Linux x86_64",
                    Optional.empty(),
                    Optional.of("rockylinux:9@sha256:floor-proof"),
                    new BundleLayoutContract.PublicBundlePublication(
                        PublicBundlePublicationStatus.PUBLISHED,
                        Optional.of("ubuntu-24.04"),
                        Optional.of("Linux"),
                        Optional.of("x86_64"))));
    assertEquals(
        "minimumGlibcVersion must be present for linux bundle targets.",
        missingLinuxFloor.getMessage());

    IllegalArgumentException missingLinuxCompatibilitySmoke =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BundleLayoutContract.BundleTarget(
                    "linux",
                    "x86_64",
                    "tar.gz",
                    "bin/fingrind",
                    "./bin/fingrind",
                    "libsqlite3.so.0",
                    "glibc 2.34+ Linux x86_64",
                    Optional.of("2.34"),
                    Optional.empty(),
                    new BundleLayoutContract.PublicBundlePublication(
                        PublicBundlePublicationStatus.PUBLISHED,
                        Optional.of("ubuntu-24.04"),
                        Optional.of("Linux"),
                        Optional.of("x86_64"))));
    assertEquals(
        "compatibilitySmokeContainerImage must be present for linux bundle targets.",
        missingLinuxCompatibilitySmoke.getMessage());

    IllegalArgumentException spuriousMacFloor =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BundleLayoutContract.BundleTarget(
                    "macos",
                    "aarch64",
                    "tar.gz",
                    "bin/fingrind",
                    "./bin/fingrind",
                    "libsqlite3.dylib",
                    "macOS aarch64",
                    Optional.of("2.34"),
                    Optional.empty(),
                    new BundleLayoutContract.PublicBundlePublication(
                        PublicBundlePublicationStatus.NOT_PUBLISHED,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty())));
    assertEquals(
        "minimumGlibcVersion must be absent for non-linux bundle targets.",
        spuriousMacFloor.getMessage());

    IllegalArgumentException spuriousMacCompatibilitySmoke =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BundleLayoutContract.BundleTarget(
                    "macos",
                    "aarch64",
                    "tar.gz",
                    "bin/fingrind",
                    "./bin/fingrind",
                    "libsqlite3.dylib",
                    "macOS aarch64",
                    Optional.empty(),
                    Optional.of("rockylinux:9@sha256:floor-proof"),
                    new BundleLayoutContract.PublicBundlePublication(
                        PublicBundlePublicationStatus.NOT_PUBLISHED,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty())));
    assertEquals(
        "compatibilitySmokeContainerImage must be absent for non-linux bundle targets.",
        spuriousMacCompatibilitySmoke.getMessage());
  }

  @Test
  void loadFromResource_rejectsExplicitNullLinuxGlibcFloor() {
    IllegalArgumentException missingLinuxFloor =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BundleLayoutContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "bundleTargets": {
                            "macos-aarch64": {
                              "operatingSystemId": "macos",
                              "architectureId": "aarch64",
                              "archiveFormat": "tar.gz",
                              "launcherPath": "bin/fingrind",
                              "launcherCommand": "./bin/fingrind",
                              "sqliteLibraryFileName": "libsqlite3.dylib",
                              "compatibilityLabel": "macOS aarch64"
                            },
                            "macos-x86_64": {
                              "operatingSystemId": "macos",
                              "architectureId": "x86_64",
                              "archiveFormat": "tar.gz",
                              "launcherPath": "bin/fingrind",
                              "launcherCommand": "./bin/fingrind",
                              "sqliteLibraryFileName": "libsqlite3.dylib",
                              "compatibilityLabel": "macOS x86_64"
                            },
                            "linux-x86_64": {
                              "operatingSystemId": "linux",
                              "architectureId": "x86_64",
                              "archiveFormat": "tar.gz",
                              "launcherPath": "bin/fingrind",
                              "launcherCommand": "./bin/fingrind",
                              "sqliteLibraryFileName": "libsqlite3.so.0",
                              "compatibilityLabel": "glibc 2.34+ Linux x86_64",
                              "minimumGlibcVersion": null,
                              "compatibilitySmokeContainerImage": "rockylinux:9@sha256:floor-proof"
                            },
                            "linux-aarch64": {
                              "operatingSystemId": "linux",
                              "architectureId": "aarch64",
                              "archiveFormat": "tar.gz",
                              "launcherPath": "bin/fingrind",
                              "launcherCommand": "./bin/fingrind",
                              "sqliteLibraryFileName": "libsqlite3.so.0",
                              "compatibilityLabel": "glibc 2.34+ Linux aarch64",
                              "minimumGlibcVersion": "2.34",
                              "compatibilitySmokeContainerImage": "rockylinux:9@sha256:floor-proof"
                            },
                            "windows-x86_64": {
                              "operatingSystemId": "windows",
                              "architectureId": "x86_64",
                              "archiveFormat": "zip",
                              "launcherPath": "bin/fingrind.ps1",
                              "launcherCommand": ".\\\\bin\\\\fingrind.ps1",
                              "sqliteLibraryFileName": "sqlite3.dll",
                              "compatibilityLabel": "Windows x86_64"
                            },
                            "windows-aarch64": {
                              "operatingSystemId": "windows",
                              "architectureId": "aarch64",
                              "archiveFormat": "zip",
                              "launcherPath": "bin/fingrind.ps1",
                              "launcherCommand": ".\\\\bin\\\\fingrind.ps1",
                              "sqliteLibraryFileName": "sqlite3.dll",
                              "compatibilityLabel": "Windows aarch64"
                            }
                          }
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/null-linux-glibc-bundle-layout.json"));

    assertEquals(
        "minimumGlibcVersion must be present for linux bundle targets.",
        missingLinuxFloor.getMessage());
  }

  @Test
  void bundleLayoutContractLoaderValidatesPublicationFieldsAndNormalizationEdges() {
    BundleLayoutContract loaded =
        BundleLayoutContracts.loadFromResources(
            new ByteArrayInputStream(
                """
                {
                  "bundleTargets": {
                    "linux-x86_64": {
                      "operatingSystemId": "linux",
                      "architectureId": "x86_64",
                      "archiveFormat": "tar.gz",
                      "launcherPath": "bin/fingrind",
                      "launcherCommand": "./bin/fingrind",
                      "sqliteLibraryFileName": "libsqlite3.so.0",
                      "compatibilityLabel": "glibc 2.34+ Linux x86_64",
                      "minimumGlibcVersion": "2.34",
                      "compatibilitySmokeContainerImage": "rockylinux:9@sha256:floor-proof"
                    },
                    "linux-aarch64": {
                      "operatingSystemId": "linux",
                      "architectureId": "aarch64",
                      "archiveFormat": "tar.gz",
                      "launcherPath": "bin/fingrind",
                      "launcherCommand": "./bin/fingrind",
                      "sqliteLibraryFileName": "libsqlite3.so.0",
                      "compatibilityLabel": "glibc 2.34+ Linux aarch64",
                      "minimumGlibcVersion": "2.34",
                      "compatibilitySmokeContainerImage": "rockylinux:9@sha256:floor-proof"
                    },
                    "macos-aarch64": {
                      "operatingSystemId": "macos",
                      "architectureId": "aarch64",
                      "archiveFormat": "tar.gz",
                      "launcherPath": "bin/fingrind",
                      "launcherCommand": "./bin/fingrind",
                      "sqliteLibraryFileName": "libsqlite3.dylib",
                      "compatibilityLabel": "macOS aarch64"
                    },
                    "macos-x86_64": {
                      "operatingSystemId": "macos",
                      "architectureId": "x86_64",
                      "archiveFormat": "tar.gz",
                      "launcherPath": "bin/fingrind",
                      "launcherCommand": "./bin/fingrind",
                      "sqliteLibraryFileName": "libsqlite3.dylib",
                      "compatibilityLabel": "macOS x86_64"
                    },
                    "windows-x86_64": {
                      "operatingSystemId": "windows",
                      "architectureId": "x86_64",
                      "archiveFormat": "zip",
                      "launcherPath": "bin/fingrind.ps1",
                      "launcherCommand": ".\\\\bin\\\\fingrind.ps1",
                      "sqliteLibraryFileName": "sqlite3.dll",
                      "compatibilityLabel": "Windows x86_64"
                    },
                    "windows-aarch64": {
                      "operatingSystemId": "windows",
                      "architectureId": "aarch64",
                      "archiveFormat": "zip",
                      "launcherPath": "bin/fingrind.ps1",
                      "launcherCommand": ".\\\\bin\\\\fingrind.ps1",
                      "sqliteLibraryFileName": "sqlite3.dll",
                      "compatibilityLabel": "Windows aarch64"
                    }
                  }
                }
                """
                    .getBytes(StandardCharsets.UTF_8)),
            "test-resource",
            new ByteArrayInputStream(
                """
                {
                  "bundleTargets": {
                    "linux-x86_64": {
                      "status": "published",
                      "runnerLabel": "ubuntu-24.04",
                      "expectedRunnerOs": "Linux",
                      "expectedRunnerArch": "x86_64"
                    },
                    "linux-aarch64": {
                      "status": "published",
                      "runnerLabel": "ubuntu-24.04-arm",
                      "expectedRunnerOs": "Linux",
                      "expectedRunnerArch": "aarch64"
                    },
                    "macos-aarch64": {
                      "status": "published",
                      "runnerLabel": "macos-15",
                      "expectedRunnerOs": "macOS",
                      "expectedRunnerArch": "arm64"
                    },
                    "macos-x86_64": {
                      "status": "published",
                      "runnerLabel": "macos-15-intel",
                      "expectedRunnerOs": "macOS",
                      "expectedRunnerArch": "x86_64"
                    },
                    "windows-x86_64": {
                      "status": "published",
                      "runnerLabel": "windows-2022",
                      "expectedRunnerOs": "Windows",
                      "expectedRunnerArch": "x86_64"
                    },
                    "windows-aarch64": {
                      "status": "not-published"
                    }
                  }
                }
                """
                    .getBytes(StandardCharsets.UTF_8)),
            "test-publication-resource");
    assertEquals(
        List.of(
            PublicCliBundleTarget.MACOS_AARCH64,
            PublicCliBundleTarget.MACOS_X86_64,
            PublicCliBundleTarget.LINUX_X86_64,
            PublicCliBundleTarget.LINUX_AARCH64,
            PublicCliBundleTarget.WINDOWS_X86_64),
        loaded.supportedPublicCliBundleTargets());
    assertEquals(
        List.of(PublicCliBundleTarget.WINDOWS_AARCH64), loaded.unsupportedPublicCliBundleTargets());
    assertEquals(
        "bundle publication contract must declare one publication object for linux-x86_64 in /dev/erst/fingrind/contract/protocol/bundle-publication-contract.json.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    BundleLayoutContracts.loadFromResources(
                        new ByteArrayInputStream(
                            """
                            {
                              "bundleTargets": {
                                "linux-x86_64": {
                                  "operatingSystemId": "linux",
                                  "architectureId": "x86_64",
                                  "archiveFormat": "tar.gz",
                                  "launcherPath": "bin/fingrind",
                                  "launcherCommand": "./bin/fingrind",
                                  "sqliteLibraryFileName": "libsqlite3.so.0",
                                  "compatibilityLabel": "glibc 2.34+ Linux x86_64",
                                  "minimumGlibcVersion": "2.34",
                                  "compatibilitySmokeContainerImage": "rockylinux:9@sha256:floor-proof"
                                }
                              }
                            }
                            """
                                .getBytes(StandardCharsets.UTF_8)),
                        "blank-resource",
                        new ByteArrayInputStream(
                            """
                            {
                              "bundleTargets": {}
                            }
                            """
                                .getBytes(StandardCharsets.UTF_8)),
                        "/dev/erst/fingrind/contract/protocol/bundle-publication-contract.json"))
            .getMessage());
    assertThrows(
        IllegalStateException.class,
        () -> BundleLayoutContracts.loadFromResource(nullOf(), "missing-resource"));
    assertThrows(
        UncheckedIOException.class,
        () -> BundleLayoutContracts.loadFromResource(failingInputStream(), "bad-resource"));
    assertEquals(
        "Unsupported bundle publication status: maybe.",
        assertThrows(
                IllegalArgumentException.class,
                () -> PublicBundlePublicationStatus.fromWireValue("maybe"))
            .getMessage());
    assertThrows(
        NullPointerException.class,
        () ->
            new BundleLayoutContract.PublicBundlePublication(
                nullOf(), Optional.empty(), Optional.empty(), Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BundleLayoutContract.PublicBundlePublication(
                PublicBundlePublicationStatus.PUBLISHED,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BundleLayoutContract.PublicBundlePublication(
                PublicBundlePublicationStatus.NOT_PUBLISHED,
                Optional.of("windows-2022"),
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            BundleLayoutContracts.loadFromResources(
                new ByteArrayInputStream(
                    """
                    {
                      "bundleTargets": {
                        "linux-x86_64": {
                          "operatingSystemId": "linux",
                          "architectureId": "x86_64",
                          "archiveFormat": "tar.gz",
                          "launcherPath": "bin/fingrind",
                          "launcherCommand": "./bin/fingrind",
                          "sqliteLibraryFileName": "libsqlite3.so.0",
                          "compatibilityLabel": "glibc 2.34+ Linux x86_64",
                          "minimumGlibcVersion": "2.34",
                          "compatibilitySmokeContainerImage": "rockylinux:9@sha256:floor-proof"
                        }
                      }
                    }
                    """
                        .getBytes(StandardCharsets.UTF_8)),
                "incomplete-publication-resource",
                new ByteArrayInputStream(
                    """
                    {
                      "bundleTargets": {
                        "linux-x86_64": {
                          "status": "published"
                        }
                      }
                    }
                    """
                        .getBytes(StandardCharsets.UTF_8)),
                "incomplete-publication-details-resource"));
  }

  private static InputStream failingInputStream() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("boom");
      }

      @Override
      public int read(byte[] buffer, int offset, int length) throws IOException {
        throw new IOException("boom");
      }
    };
  }
}
