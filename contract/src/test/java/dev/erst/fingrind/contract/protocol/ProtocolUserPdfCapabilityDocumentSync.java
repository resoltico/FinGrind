package dev.erst.fingrind.contract.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Synchronizes descriptor-owned PDF report inventories in user documentation. */
final class ProtocolUserPdfCapabilityDocumentSync {
  static final List<String> DOCUMENT_PATHS =
      List.of("docs/USER_CLI.md", "docs/USER_CLI_OPERATIONAL_NOTES.md", "docs/USER_RESPONSES.md");

  private ProtocolUserPdfCapabilityDocumentSync() {}

  static void sync(Path repositoryRoot) throws IOException {
    Path normalizedRepositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot");
    for (String relativeDocumentPath : DOCUMENT_PATHS) {
      Path documentPath = normalizedRepositoryRoot.resolve(relativeDocumentPath);
      String original = Files.readString(documentPath).replace("\r\n", "\n");
      String updated = updatedDocument(original, relativeDocumentPath);
      if (!original.equals(updated)) {
        Files.writeString(documentPath, updated);
      }
    }
  }

  static String updatedDocument(String document, String relativeDocumentPath) {
    String normalizedDocument = Objects.requireNonNull(document, "document").replace("\r\n", "\n");
    String description = Objects.requireNonNull(relativeDocumentPath, "relativeDocumentPath");
    int beginIndex =
        uniqueMarkerIndex(normalizedDocument, beginMarker(), description + " begin marker");
    int endIndex = uniqueMarkerIndex(normalizedDocument, endMarker(), description + " end marker");
    if (endIndex < beginIndex) {
      throw new IllegalArgumentException(
          description + " end marker must appear after the begin marker.");
    }
    String before = normalizedDocument.substring(0, beginIndex).stripTrailing();
    String after = normalizedDocument.substring(endIndex + endMarker().length()).stripLeading();
    String inventory = ProtocolUserPdfCapabilityMarkdownRenderer.pdfReportInventoryBlock();
    return after.isEmpty()
        ? before + "\n\n" + inventory + "\n"
        : before + "\n\n" + inventory + "\n\n" + after;
  }

  private static String beginMarker() {
    return ProtocolUserPdfCapabilityMarkdownRenderer.PDF_REPORT_INVENTORY_BEGIN;
  }

  private static String endMarker() {
    return ProtocolUserPdfCapabilityMarkdownRenderer.PDF_REPORT_INVENTORY_END;
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
