package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/** Contract-lint tests for public install and container user guides. */
class ProtocolUserInstallDocsContractTest extends ProtocolContractRepositorySupport {
  @Test
  void userInstallGuide_matchesCanonicalGeneratedPackageSurface() throws IOException {
    String document =
        Files.readString(repositoryRoot().resolve("docs/USER_INSTALL.md")).replace("\r\n", "\n");

    assertEquals(
        document,
        ProtocolUserInstallDocumentSync.updatedUserInstallDocument(repositoryRoot(), document),
        "docs/USER_INSTALL.md must stay synchronized with the canonical public package metadata renderer.");
  }

  @Test
  void userQuickStartGuide_matchesCanonicalGeneratedBundleMatrix() throws IOException {
    String document =
        Files.readString(repositoryRoot().resolve("docs/USER_QUICK_START.md"))
            .replace("\r\n", "\n");

    assertEquals(
        document,
        ProtocolUserInstallDocumentSync.updatedUserQuickStartDocument(repositoryRoot(), document),
        "docs/USER_QUICK_START.md must stay synchronized with the canonical public bundle matrix renderer.");
  }

  @Test
  void publishedContainerGuide_exposesTheLiveImageReferenceAndMountedWorkspaceContract()
      throws IOException {
    String document = Files.readString(repositoryRoot().resolve("docs/USER_CONTAINER.md"));

    assertTrue(document.contains("ghcr.io/resoltico/fingrind"));
    assertTrue(document.contains("linux/amd64"));
    assertTrue(document.contains("linux/arm64"));
    assertTrue(document.contains("-v \"$PWD\":/workspace"));
    assertTrue(document.contains("-w /workspace"));
    assertTrue(
        document.contains("generate-book-key-file --new-book-key-file ./secrets/acme.book-key"));
    assertTrue(document.contains("print-request-template > ./request.json"));
    assertTrue(document.contains("--pdf-out ./trial-balance.pdf"));
  }
}
