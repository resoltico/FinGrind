package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression tests for the USER_INSTALL and USER_QUICK_START document-sync launcher. */
class ProtocolUserInstallDocumentSyncMainTest extends ProtocolContractRepositorySupport {
  @Test
  void main_requiresExactlyOneArgument() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ProtocolUserInstallDocumentSyncMain.main(new String[0]));

    assertTrue(
        Objects.requireNonNull(exception.getMessage()).contains("Expected exactly one argument"));
  }

  @Test
  void main_synchronizesTheRequestedRepositoryDocs(@TempDir Path tempDir) throws IOException {
    Path repositoryRoot = tempDir.resolve("repo");
    Path docsDirectory = repositoryRoot.resolve("docs");
    Files.createDirectories(docsDirectory);
    Path protocolDirectory =
        repositoryRoot.resolve("contract/src/main/resources/dev/erst/fingrind/contract/protocol");
    Files.createDirectories(protocolDirectory);

    Path sourceProtocolDirectory =
        repositoryRoot().resolve("contract/src/main/resources/dev/erst/fingrind/contract/protocol");
    Files.copy(
        sourceProtocolDirectory.resolve("bundle-layout-contract.json"),
        protocolDirectory.resolve("bundle-layout-contract.json"));
    Files.copy(
        sourceProtocolDirectory.resolve("release-publication-contract.json"),
        protocolDirectory.resolve("release-publication-contract.json"));
    Files.writeString(repositoryRoot.resolve("gradle.properties"), "version=9.8.7\n");

    Path userInstall = docsDirectory.resolve("USER_INSTALL.md");
    Files.writeString(
        userInstall,
        """
        Header

        <!-- BEGIN GENERATED USER_INSTALL PACKAGE MATRIX -->
        old package block
        <!-- END GENERATED USER_INSTALL PACKAGE MATRIX -->

        Middle

        <!-- BEGIN GENERATED USER_INSTALL CONTAINER SURFACE -->
        old container block
        <!-- END GENERATED USER_INSTALL CONTAINER SURFACE -->
        """);
    Path quickStart = docsDirectory.resolve("USER_QUICK_START.md");
    Files.writeString(
        quickStart,
        """
        Header

        <!-- BEGIN GENERATED USER_QUICK_START BUNDLE MATRIX -->
        old quick start block
        <!-- END GENERATED USER_QUICK_START BUNDLE MATRIX -->
        """);

    ProtocolUserInstallDocumentSyncMain.main(new String[] {repositoryRoot.toString()});

    assertEquals(
        ProtocolUserInstallDocumentSync.updatedUserInstallDocument(
            repositoryRoot, Files.readString(userInstall)),
        Files.readString(userInstall));
    assertEquals(
        ProtocolUserInstallDocumentSync.updatedUserQuickStartDocument(
            repositoryRoot, Files.readString(quickStart)),
        Files.readString(quickStart));
    assertTrue(Files.readString(userInstall).contains("one exact release tag such as `9.8.7`"));
  }

  @Test
  void containerSurface_rejectsMissingAmbiguousBlankAndNonReleaseProjectVersions(
      @TempDir Path tempDir) throws IOException {
    for (String properties :
        java.util.List.of("", "version=1.2.3\nversion=2.3.4\n", "version=\n", "version=1.2\n")) {
      Path repositoryRoot = tempDir.resolve("repo-" + properties.hashCode());
      Path protocolDirectory =
          repositoryRoot.resolve("contract/src/main/resources/dev/erst/fingrind/contract/protocol");
      Files.createDirectories(protocolDirectory);
      Files.copy(
          repositoryRoot()
              .resolve(
                  "contract/src/main/resources/dev/erst/fingrind/contract/protocol/release-publication-contract.json"),
          protocolDirectory.resolve("release-publication-contract.json"));
      Files.writeString(repositoryRoot.resolve("gradle.properties"), properties);

      assertThrows(
          IOException.class,
          () ->
              ProtocolUserInstallMarkdownRenderer.userInstallContainerSurfaceBlock(repositoryRoot));
    }
  }
}
