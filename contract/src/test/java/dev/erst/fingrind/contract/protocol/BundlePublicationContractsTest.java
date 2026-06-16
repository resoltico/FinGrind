package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
                    PublicBundlePublicationStatus.PUBLISHED, Optional.of("ubuntu-24.04"))));

    IllegalArgumentException missingTargets =
        assertThrows(
            IllegalArgumentException.class,
            () -> contract.requireComplete("/bundle-publication-contract.json"));

    assertEquals(
        "bundleTargets must cover every public CLI bundle target in /bundle-publication-contract.json: [macos-aarch64, macos-x86_64, linux-aarch64, windows-x86_64, windows-aarch64]",
        missingTargets.getMessage());
  }

  @Test
  void loadFromResource_normalizesExplicitNullRunnerMetadataForNonPublishedTargets() {
    BundlePublicationContract contract =
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
            "/bundle-publication.json");

    BundleLayoutContract.PublicBundlePublication publication =
        Objects.requireNonNull(contract.bundleTargets().get(PublicCliBundleTarget.WINDOWS_AARCH64));
    assertEquals(PublicBundlePublicationStatus.NOT_PUBLISHED, publication.status());
    assertEquals(Optional.empty(), publication.runnerLabel());
  }

  @Test
  void publicBundlePublication_enforcesPublishedAndNonPublishedRunnerMetadataShapes() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BundleLayoutContract.PublicBundlePublication(
                PublicBundlePublicationStatus.PUBLISHED, Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BundleLayoutContract.PublicBundlePublication(
                PublicBundlePublicationStatus.NOT_PUBLISHED, Optional.of("ubuntu-24.04")));
  }
}
