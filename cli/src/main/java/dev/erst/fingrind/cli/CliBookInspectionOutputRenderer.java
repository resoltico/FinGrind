package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.BookInspection;
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
    rows.add(List.of("State", displayStatus(status)));
    rows.add(
        List.of("Initialized", CliQueryOutputFormatter.displayBooleanLabel(status.initialized())));
    rows.add(
        List.of(
            "Compatible with current binary",
            CliQueryOutputFormatter.displayBooleanLabel(status.compatibleWithCurrentBinary())));
    rows.add(
        List.of(
            CliOperationText.initializeWithOpenBookLabel(),
            CliQueryOutputFormatter.displayBooleanLabel(status.canInitializeWithOpenBook())));
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
      case BookInspection.Initialized initialized -> initializedDetailRows(initialized);
    };
  }

  private static List<List<String>> initializedDetailRows(BookInspection.Initialized initialized) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("SQLite applicationId", Integer.toString(initialized.applicationId())));
    rows.add(
        List.of(
            "Detected book format version",
            Integer.toString(initialized.detectedBookFormatVersion())));
    rows.addAll(CliBookIdentityDisplay.rows(initialized.bookIdentity()));
    rows.add(List.of("Initialized at", CliHumanDisplay.instant(initialized.initializedAt())));
    return List.copyOf(rows);
  }

  private static String displayStatus(BookInspection.Status status) {
    return switch (status) {
      case MISSING -> "Missing";
      case BLANK_SQLITE -> "Blank SQLite";
      case FOREIGN_SQLITE -> "Foreign SQLite";
      case UNSUPPORTED_FORMAT_VERSION -> "Unsupported format version";
      case INCOMPLETE_FINGRIND -> "Incomplete FinGrind";
      case INITIALIZED -> "Initialized";
    };
  }
}
