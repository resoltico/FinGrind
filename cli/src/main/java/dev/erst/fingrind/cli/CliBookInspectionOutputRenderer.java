package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.BookMigrationPolicy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Renders book-inspection payloads for text output. */
final class CliBookInspectionOutputRenderer {
  private static final int TEXT_WRAP_WIDTH = 96;

  private CliBookInspectionOutputRenderer() {}

  static String renderText(Path bookFilePath, BookInspection inspection) {
    BookInspection.Status status = inspection.status();
    return CliTextFormat.renderTitledBlock(
        "Book Inspection",
        joinSections(
            section(
                "Book State",
                CliTextFormat.renderKeyValueBlock(
                    List.of(
                        List.of("Book file", CliTextDisplay.path(bookFilePath)),
                        List.of("State", displayStatus(status)),
                        List.of(
                            "Initialized",
                            CliQueryScopeText.displayBooleanLabel(status.initialized())),
                        List.of(
                            "Compatible with current binary",
                            CliQueryScopeText.displayBooleanLabel(
                                status.compatibleWithCurrentBinary())),
                        List.of(
                            CliOperationText.initializeWithOpenBookLabel(),
                            CliQueryScopeText.displayBooleanLabel(
                                status.canInitializeWithOpenBook()))),
                    TEXT_WRAP_WIDTH)),
            section(
                "Format Compatibility",
                CliTextFormat.renderKeyValueBlock(formatRows(inspection), TEXT_WRAP_WIDTH)),
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
            CliQueryScopeText.displayBooleanLabel(migrationPolicy.inPlaceUpgradeSupported())),
        List.of(
            "Older book formats accepted",
            CliQueryScopeText.displayBooleanLabel(migrationPolicy.olderFormatsAccepted())),
        List.of(
            "Newer book formats accepted",
            CliQueryScopeText.displayBooleanLabel(migrationPolicy.newerFormatsAccepted())));
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
          CliTextFormat.renderKeyValueBlock(initializedIdentityRows(initialized), TEXT_WRAP_WIDTH));
    }
    return "";
  }

  private static List<List<String>> initializedIdentityRows(
      BookInspection.Initialized initialized) {
    List<List<String>> rows =
        new ArrayList<>(CliBookIdentityDisplay.rows(initialized.bookIdentity()));
    rows.add(List.of("Initialized at", CliTextDisplay.instant(initialized.initializedAt())));
    return List.copyOf(rows);
  }

  private static String closeReadinessSection(BookInspection inspection) {
    if (!(inspection instanceof BookInspection.Initialized initialized)) {
      return "";
    }
    return joinSections(
        section(
            "Interim Close Target",
            CliTextFormat.renderKeyValueBlock(
                closeTargetReadinessRows(initialized.closeReadiness().interimResultTarget()),
                TEXT_WRAP_WIDTH)),
        section(
            "Retained Close Target",
            CliTextFormat.renderKeyValueBlock(
                closeTargetReadinessRows(initialized.closeReadiness().retainedAccumulatedTarget()),
                TEXT_WRAP_WIDTH)));
  }

  private static List<List<String>> closeTargetReadinessRows(
      BookInspection.CloseTargetReadiness closeTargetReadiness) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Ready", CliQueryScopeText.displayBooleanLabel(closeTargetReadiness.ready())));
    rows.add(
        List.of(
            "Required classification",
            CliAccountStatementLabels.displayFinancialPositionLineClassification(
                closeTargetReadiness.requiredFinancialPositionLineClassification())));
    rows.add(
        List.of(
            "Account",
            closeTargetReadiness.accountCode() == null
                ? "(none)"
                : closeTargetReadiness.accountCode().value()));
    if (!closeTargetReadiness.ready()) {
      rows.add(List.of("Blocking code", closeTargetReadiness.blockingCode()));
      rows.add(List.of("Blocking reason", closeTargetReadiness.blockingMessage()));
      rows.add(
          List.of(
              "Candidate accounts",
              closeTargetReadiness.candidateAccountCodes().isEmpty()
                  ? "(none)"
                  : closeTargetReadiness.candidateAccountCodes().stream()
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
      case HARD_BREAK_REJECT_NONCURRENT_FORMATS -> "Hard-break line; reject noncurrent formats";
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
