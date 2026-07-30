package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Coverage tests for the canonical bundle-publication contract loaders and facts. */
class BundlePublicationContractsTest {
  @Test
  void publicationStatusWireValuesRemainCanonical() {
    assertEquals("published", PublicBundlePublicationStatus.PUBLISHED.wireValue());
    assertEquals("not-published", PublicBundlePublicationStatus.NOT_PUBLISHED.wireValue());
  }

  @Test
  void bundlePublicationContract_requireCompleteRejectsMissingTargets() {
    BundlePublicationContract contract =
        new BundlePublicationContract(
            Map.of(
                PublicCliBundleTarget.LINUX_X86_64,
                new BundleLayoutContract.PublicBundlePublication(
                    PublicBundlePublicationStatus.PUBLISHED)));

    IllegalArgumentException missingTargets =
        assertThrows(
            IllegalArgumentException.class,
            () -> contract.requireComplete("/bundle-publication-contract.json"));

    assertEquals(
        "bundleTargets must cover every public CLI bundle target in /bundle-publication-contract.json: [macos-aarch64, macos-x86_64, linux-aarch64, windows-x86_64, windows-aarch64]",
        missingTargets.getMessage());
  }

  @Test
  void loadFromResource_rejectsRetiredRunnerMetadata() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BundlePublicationContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "bundleTargets": {
                            "windows-aarch64": {
                              "status": "not-published",
                              "runnerLabel": null
                            }
                          }
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/bundle-publication.json"));

    assertEquals(
        "bundleTargets entry must not declare unrecognized properties: runnerLabel",
        failure.getMessage());
  }

  @Test
  void publicBundlePublication_preservesPublicationStatusOnly() {
    BundleLayoutContract.PublicBundlePublication publication =
        new BundleLayoutContract.PublicBundlePublication(PublicBundlePublicationStatus.PUBLISHED);

    assertEquals(PublicBundlePublicationStatus.PUBLISHED, publication.status());
  }
}
