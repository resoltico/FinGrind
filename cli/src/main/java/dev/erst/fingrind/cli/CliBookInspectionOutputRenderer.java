package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.BookInspection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Renders book-inspection payloads for human output. */
final class CliBookInspectionOutputRenderer {
  private CliBookInspectionOutputRenderer() {}

  static String renderHuman(Path bookFilePath, BookInspection inspection) {
    BookInspection.Status status = inspection.status();
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Book file", CliQueryOutputFormatter.absolutePath(bookFilePath)));
    rows.add(List.of("State", status.wireValue()));
    rows.add(List.of("Initialized", Boolean.toString(status.initialized())));
    rows.add(
        List.of(
            "Compatible with current binary",
            Boolean.toString(status.compatibleWithCurrentBinary())));
    rows.add(
        List.of(
            CliOperationText.initializeWithOpenBookLabel(),
            Boolean.toString(status.canInitializeWithOpenBook())));
    rows.add(
        List.of(
            "Supported book format version",
            Integer.toString(inspection.supportedBookFormatVersion())));
    rows.addAll(detailRows(inspection));
    return CliTextFormat.renderTitledBlock(
        "Book Inspection", CliTextFormat.renderKeyValueBlock(rows));
  }

  private static List<List<String>> detailRows(BookInspection inspection) {
    return switch (inspection) {
      case BookInspection.Missing _ -> List.of();
      case BookInspection.Existing existing ->
          List.of(
              List.of("SQLite applicationId", Integer.toString(existing.applicationId())),
              List.of(
                  "Detected book format version",
                  Integer.toString(existing.detectedBookFormatVersion())));
      case BookInspection.Initialized initialized ->
          List.of(
              List.of("SQLite applicationId", Integer.toString(initialized.applicationId())),
              List.of(
                  "Detected book format version",
                  Integer.toString(initialized.detectedBookFormatVersion())),
              List.of("Initialized at", initialized.initializedAt().toString()));
    };
  }
}
