package dev.erst.fingrind.contract.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Synchronizes the generated USER_CLI command-table block from the canonical protocol catalog. */
final class ProtocolUserCliDocumentSync {
  private ProtocolUserCliDocumentSync() {}

  static void sync(Path documentPath) throws IOException {
    Path normalizedPath = Objects.requireNonNull(documentPath, "documentPath").toAbsolutePath();
    String original = Files.readString(normalizedPath).replace("\r\n", "\n");
    String updated = updatedDocument(original);
    if (!original.equals(updated)) {
      Files.writeString(normalizedPath, updated);
    }
  }

  static String updatedDocument(String document) {
    String normalizedDocument = Objects.requireNonNull(document, "document").replace("\r\n", "\n");
    int beginIndex = beginMarkerIndex(normalizedDocument);
    int endIndex = endMarkerIndex(normalizedDocument);
    if (endIndex < beginIndex) {
      throw new IllegalArgumentException(
          "docs/USER_CLI.md generated command-table end marker must appear after the begin marker.");
    }
    String before = normalizedDocument.substring(0, beginIndex).stripTrailing();
    String after =
        normalizedDocument
            .substring(endIndex + ProtocolUserCliMarkdownRenderer.COMMAND_TABLE_END.length())
            .stripLeading();
    String generatedBlock = ProtocolUserCliMarkdownRenderer.commandTableBlock();
    if (after.isEmpty()) {
      return before + "\n\n" + generatedBlock + "\n";
    }
    return before + "\n\n" + generatedBlock + "\n\n" + after;
  }

  private static int beginMarkerIndex(String document) {
    int beginIndex = document.indexOf(ProtocolUserCliMarkdownRenderer.COMMAND_TABLE_BEGIN);
    if (beginIndex < 0) {
      throw new IllegalArgumentException(
          "docs/USER_CLI.md is missing the generated command-table begin marker.");
    }
    if (document.indexOf(ProtocolUserCliMarkdownRenderer.COMMAND_TABLE_BEGIN, beginIndex + 1)
        >= 0) {
      throw new IllegalArgumentException(
          "docs/USER_CLI.md must contain only one generated command-table begin marker.");
    }
    return beginIndex;
  }

  private static int endMarkerIndex(String document) {
    int endIndex = document.indexOf(ProtocolUserCliMarkdownRenderer.COMMAND_TABLE_END);
    if (endIndex < 0) {
      throw new IllegalArgumentException(
          "docs/USER_CLI.md is missing the generated command-table end marker.");
    }
    if (document.indexOf(ProtocolUserCliMarkdownRenderer.COMMAND_TABLE_END, endIndex + 1) >= 0) {
      throw new IllegalArgumentException(
          "docs/USER_CLI.md must contain only one generated command-table end marker.");
    }
    return endIndex;
  }
}
