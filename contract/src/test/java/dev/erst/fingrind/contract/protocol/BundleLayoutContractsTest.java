package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Coverage tests for the canonical per-target bundle-layout contract loader. */
class BundleLayoutContractsTest {
  @Test
  void current_coversEveryPublicCliBundleTarget() {
    BundleLayoutContract current = BundleLayoutContracts.current();

    assertEquals(EnumSet.allOf(PublicCliBundleTarget.class), current.bundleTargets().keySet());
    assertEquals(current, ProtocolCatalogFacts.bundleLayoutContract());
    assertEquals(
        "zip", current.bundleTarget(PublicCliBundleTarget.WINDOWS_AARCH64).archiveFormat());
    assertEquals(
        "sqlite3.dll",
        current.bundleTarget(PublicCliBundleTarget.WINDOWS_AARCH64).sqliteLibraryFileName());
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
                            "libsqlite3.so.0"))));

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
                            "libsqlite3.so.0")),
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
                              "sqliteLibraryFileName": "libsqlite3.so.0"
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
}
