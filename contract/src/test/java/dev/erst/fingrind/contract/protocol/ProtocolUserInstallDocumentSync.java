package dev.erst.fingrind.contract.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Synchronizes the generated public package metadata blocks in user install documents. */
final class ProtocolUserInstallDocumentSync {
  private ProtocolUserInstallDocumentSync() {}

  static String updatedUserInstallDocument(Path repositoryRoot, String document)
      throws IOException {
    String updated =
        replaceGeneratedBlock(
            document,
            ProtocolUserInstallMarkdownRenderer.USER_INSTALL_PACKAGE_MATRIX_BEGIN,
            ProtocolUserInstallMarkdownRenderer.USER_INSTALL_PACKAGE_MATRIX_END,
            ProtocolUserInstallMarkdownRenderer.userInstallPackageMatrixBlock(repositoryRoot),
            "docs/USER_INSTALL.md package matrix");
    return replaceGeneratedBlock(
        updated,
        ProtocolUserInstallMarkdownRenderer.USER_INSTALL_CONTAINER_SURFACE_BEGIN,
        ProtocolUserInstallMarkdownRenderer.USER_INSTALL_CONTAINER_SURFACE_END,
        ProtocolUserInstallMarkdownRenderer.userInstallContainerSurfaceBlock(repositoryRoot),
        "docs/USER_INSTALL.md container surface");
  }

  static String updatedUserQuickStartDocument(Path repositoryRoot, String document)
      throws IOException {
    return replaceGeneratedBlock(
        document,
        ProtocolUserInstallMarkdownRenderer.USER_QUICK_START_BUNDLE_MATRIX_BEGIN,
        ProtocolUserInstallMarkdownRenderer.USER_QUICK_START_BUNDLE_MATRIX_END,
        ProtocolUserInstallMarkdownRenderer.userQuickStartBundleMatrixBlock(repositoryRoot),
        "docs/USER_QUICK_START.md bundle matrix");
  }

  static void syncUserInstall(Path repositoryRoot, Path documentPath) throws IOException {
    syncDocument(
        documentPath,
        updatedUserInstallDocument(
            repositoryRoot, Files.readString(documentPath).replace("\r\n", "\n")));
  }

  static void syncUserQuickStart(Path repositoryRoot, Path documentPath) throws IOException {
    syncDocument(
        documentPath,
        updatedUserQuickStartDocument(
            repositoryRoot, Files.readString(documentPath).replace("\r\n", "\n")));
  }

  private static void syncDocument(Path documentPath, String updatedDocument) throws IOException {
    Path normalizedPath = Objects.requireNonNull(documentPath, "documentPath").toAbsolutePath();
    String original = Files.readString(normalizedPath).replace("\r\n", "\n");
    if (!original.equals(updatedDocument)) {
      Files.writeString(normalizedPath, updatedDocument);
    }
  }

  private static String replaceGeneratedBlock(
      String document,
      String beginMarker,
      String endMarker,
      String replacement,
      String description) {
    String normalizedDocument = Objects.requireNonNull(document, "document").replace("\r\n", "\n");
    int beginIndex =
        uniqueMarkerIndex(normalizedDocument, beginMarker, description + " begin marker");
    int endIndex = uniqueMarkerIndex(normalizedDocument, endMarker, description + " end marker");
    if (endIndex < beginIndex) {
      throw new IllegalArgumentException(
          description + " end marker must appear after the begin marker.");
    }
    String before = normalizedDocument.substring(0, beginIndex).stripTrailing();
    String after = normalizedDocument.substring(endIndex + endMarker.length()).stripLeading();
    if (after.isEmpty()) {
      return before + "\n\n" + replacement + "\n";
    }
    return before + "\n\n" + replacement + "\n\n" + after;
  }

  private static int uniqueMarkerIndex(String document, String marker, String description) {
    int markerIndex = document.indexOf(marker);
    if (markerIndex < 0) {
      throw new IllegalArgumentException("Missing " + description + ".");
    }
    if (document.indexOf(marker, markerIndex + 1) >= 0) {
      throw new IllegalArgumentException("Duplicate " + description + ".");
    }
    return markerIndex;
  }
}
