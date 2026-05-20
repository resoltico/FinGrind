package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.BookInspection.CloseReadiness;
import dev.erst.fingrind.contract.runtime.BookMigrationPolicy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Renders book-inspection payloads for human output. */
final class CliBookInspectionOutputRenderer {
  private static final int HUMAN_WRAP_WIDTH = 96;

  private CliBookInspectionOutputRenderer() {}

  static String renderHuman(Path bookFilePath, BookInspection inspection) {
    BookInspection.Status status = inspection.status();
    return CliTextFormat.renderTitledBlock(
        "Book Inspection",
        joinSections(
            section(
                "Book State",
                CliTextFormat.renderKeyValueBlock(
                    List.of(
                        List.of("Book file", CliHumanDisplay.path(bookFilePath)),
                        List.of("State", displayStatus(status)),
                        List.of(
                            "Initialized",
                            CliQueryOutputFormatter.displayBooleanLabel(status.initialized())),
                        List.of(
                            "Compatible with current binary",
                            CliQueryOutputFormatter.displayBooleanLabel(
                                status.compatibleWithCurrentBinary())),
                        List.of(
                            CliOperationText.initializeWithOpenBookLabel(),
                            CliQueryOutputFormatter.displayBooleanLabel(
                                status.canInitializeWithOpenBook()))),
                    HUMAN_WRAP_WIDTH)),
            section(
                "Format Compatibility",
                CliTextFormat.renderKeyValueBlock(formatRows(inspection), HUMAN_WRAP_WIDTH)),
            closeReadinessSection(inspection),
            identitySection(inspection)));
  }

  private static List<List<String>> formatRows(BookInspection inspection) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(
        List.of(
            "Supported book format version",
            Integer.toString(inspection.supportedBookFormatVersion())));
    rows.addAll(migrationPolicyRows(inspection.migrationPolicy()));
    rows.addAll(formatDetailRows(inspection));
    return List.copyOf(rows);
  }

  private static List<List<String>> migrationPolicyRows(BookMigrationPolicy migrationPolicy) {
    return List.of(
        List.of("Migration policy", displayMigrationPolicyMode(migrationPolicy.mode())),
        List.of(
            "In-place upgrade supported",
            CliQueryOutputFormatter.displayBooleanLabel(migrationPolicy.inPlaceUpgradeSupported())),
        List.of(
            "Older book formats accepted",
            CliQueryOutputFormatter.displayBooleanLabel(migrationPolicy.olderFormatsAccepted())),
        List.of(
            "Newer book formats accepted",
            CliQueryOutputFormatter.displayBooleanLabel(migrationPolicy.newerFormatsAccepted())));
  }

  private static List<List<String>> formatDetailRows(BookInspection inspection) {
    return switch (inspection) {
      case BookInspection.Missing _ -> List.of();
      case BookInspection.Existing existing ->
          List.of(
              List.of("SQLite applicationId", Integer.toString(existing.applicationId())),
              List.of(
                  "Detected book format version",
                  Integer.toString(existing.detectedBookFormatVersion())));
      case BookInspection.Initialized initialized -> initializedFormatRows(initialized);
    };
  }

  private static List<List<String>> initializedFormatRows(BookInspection.Initialized initialized) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("SQLite applicationId", Integer.toString(initialized.applicationId())));
    rows.add(
        List.of(
            "Detected book format version",
            Integer.toString(initialized.detectedBookFormatVersion())));
    return List.copyOf(rows);
  }

  private static String identitySection(BookInspection inspection) {
    if (inspection instanceof BookInspection.Initialized initialized) {
      return section(
          "Book Identity",
          CliTextFormat.renderKeyValueBlock(
              initializedIdentityRows(initialized), HUMAN_WRAP_WIDTH));
    }
    return "";
  }

  private static List<List<String>> initializedIdentityRows(
      BookInspection.Initialized initialized) {
    List<List<String>> rows =
        new ArrayList<>(CliBookIdentityDisplay.rows(initialized.bookIdentity()));
    rows.add(List.of("Initialized at", CliHumanDisplay.instant(initialized.initializedAt())));
    return List.copyOf(rows);
  }

  private static String closeReadinessSection(BookInspection inspection) {
    if (!(inspection instanceof BookInspection.Initialized initialized)) {
      return "";
    }
    return section(
        "Close Readiness",
        CliTextFormat.renderKeyValueBlock(
            closeReadinessRows(initialized.closeReadiness()), HUMAN_WRAP_WIDTH));
  }

  private static List<List<String>> closeReadinessRows(CloseReadiness closeReadiness) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Ready", CliQueryOutputFormatter.displayBooleanLabel(closeReadiness.ready())));
    rows.add(
        List.of(
            "Required closing-equity classification",
            CliQueryOutputFormatter.displayFinancialPositionLineClassification(
                closeReadiness.requiredFinancialPositionLineClassification())));
    rows.add(
        List.of(
            "Closing equity account",
            closeReadiness.closingEquityAccountCode() == null
                ? "(none)"
                : closeReadiness.closingEquityAccountCode().value()));
    if (!closeReadiness.ready()) {
      rows.add(List.of("Blocking code", closeReadiness.blockingCode()));
      rows.add(List.of("Blocking reason", closeReadiness.blockingMessage()));
      rows.add(
          List.of(
              "Candidate accounts",
              closeReadiness.candidateAccountCodes().isEmpty()
                  ? "(none)"
                  : closeReadiness.candidateAccountCodes().stream()
                      .map(dev.erst.fingrind.core.AccountCode::value)
                      .collect(java.util.stream.Collectors.joining(", "))));
    }
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

  private static String displayMigrationPolicyMode(
      dev.erst.fingrind.contract.runtime.BookMigrationPolicyMode mode) {
    return switch (mode) {
      case HARD_BREAK_REJECT_OLDER_FORMATS -> "Hard-break line; reject older formats";
    };
  }

  private static String section(String title, String body) {
    return title
        + System.lineSeparator()
        + "-".repeat(title.length())
        + System.lineSeparator()
        + body;
  }

  private static String joinSections(String... sections) {
    return java.util.Arrays.stream(sections)
        .filter(section -> !section.isBlank())
        .collect(
            java.util.stream.Collectors.joining(System.lineSeparator() + System.lineSeparator()));
  }
}
