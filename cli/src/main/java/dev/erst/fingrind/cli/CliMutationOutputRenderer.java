package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.ClosedPeriod;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RecoverRekeyResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.nio.file.Path;
import java.util.List;

/** Shared human-readable rendering for administrative and write command successes. */
final class CliMutationOutputRenderer {
  private CliMutationOutputRenderer() {}

  static String renderGeneratedBookKeyFileHuman(
      SqliteBookKeyFileGenerator.GeneratedKeyFile generatedKeyFile) {
    return CliTextFormat.renderTitledBlock(
        "Book Key File Generated",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book key file", absolutePath(generatedKeyFile.bookKeyFilePath())),
                List.of("Encoding", generatedKeyFile.encoding()),
                List.of("Entropy bits", Integer.toString(generatedKeyFile.entropyBits())),
                List.of("Permissions", generatedKeyFile.permissions()))));
  }

  static String renderOpenBookHuman(Path bookFilePath, OpenBookResult.Opened opened) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Book file", absolutePath(bookFilePath)));
    rows.addAll(CliBookIdentityDisplay.rows(opened.bookIdentity()));
    rows.add(List.of("Initialized at", CliHumanDisplay.instant(opened.initializedAt())));
    return CliTextFormat.renderTitledBlock(
        "Book Initialized", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  static String renderRekeyBookHuman(
      RekeyBookResult.Rekeyed rekeyed, BookAccess.PassphraseSource replacementPassphraseSource) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Book file", absolutePath(rekeyed.bookFilePath())));
    rows.add(
        List.of(
            "Replacement secret source", displayPassphraseSourceKind(replacementPassphraseSource)));
    if (replacementPassphraseSource instanceof BookAccess.PassphraseSource.KeyFile keyFile) {
      rows.add(List.of("Replacement key file", absolutePath(keyFile.bookKeyFilePath())));
    }
    return CliTextFormat.renderTitledBlock(
        "Book Rekeyed", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  static String renderBackupBookHuman(BackupBookResult.BackedUp backedUp) {
    return CliTextFormat.renderTitledBlock(
        "Book Backed Up",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", absolutePath(backedUp.bookFilePath())),
                List.of("Backup file", absolutePath(backedUp.backupFilePath())),
                List.of("Backup key file", absolutePath(backedUp.backupBookKeyFilePath())))));
  }

  static String renderRestoreBookHuman(RestoreBookResult.Restored restored) {
    return CliTextFormat.renderTitledBlock(
        "Book Restored",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", absolutePath(restored.bookFilePath())),
                List.of("Backup file", absolutePath(restored.backupFilePath())),
                List.of("Book key file", absolutePath(restored.backupBookKeyFilePath())))));
  }

  static String renderRecoverRekeyInspectionHuman(RecoverRekeyResult.Inspected inspected) {
    String rollbackArtifacts =
        inspected.rollbackArtifactPaths().isEmpty()
            ? "(none)"
            : inspected.rollbackArtifactPaths().stream()
                .map(CliMutationOutputRenderer::absolutePath)
                .collect(java.util.stream.Collectors.joining(", "));
    return CliTextFormat.renderTitledBlock(
        "Rekey Rollback Artifacts",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", absolutePath(inspected.bookFilePath())),
                List.of("Rollback artifacts", rollbackArtifacts))));
  }

  static String renderRecoverRekeyRestoredHuman(RecoverRekeyResult.Restored restored) {
    return CliTextFormat.renderTitledBlock(
        "Book Restored From Rollback",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", absolutePath(restored.bookFilePath())),
                List.of("Rollback artifact", absolutePath(restored.rollbackArtifactPath())))));
  }

  static String renderRecoverRekeyDeletedHuman(RecoverRekeyResult.Deleted deleted) {
    return CliTextFormat.renderTitledBlock(
        "Rollback Artifact Deleted",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", absolutePath(deleted.bookFilePath())),
                List.of("Rollback artifact", absolutePath(deleted.rollbackArtifactPath())))));
  }

  static String renderDeclaredAccountHuman(DeclaredAccount account) {
    return CliTextFormat.renderTitledBlock(
        "Account Declared",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Account code", account.accountCode().value()),
                List.of("Account name", account.accountName().value()),
                List.of("Account type", account.accountType().wireValue()),
                List.of("Account role", account.accountRole().wireValue()),
                List.of("Normal balance", account.normalBalance().wireValue()),
                List.of("Active", Boolean.toString(account.active())),
                List.of("Declared at", CliHumanDisplay.instant(account.declaredAt())))));
  }

  static String renderClosedPeriodHuman(ClosedPeriod closedPeriod) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Close order", Integer.toString(closedPeriod.closeOrder())));
    rows.add(
        List.of(
            "Effective date range",
            closedPeriod.reportingPeriod().effectiveDateFrom()
                + " to "
                + closedPeriod.reportingPeriod().effectiveDateTo()));
    rows.add(List.of("Closing equity account", closedPeriod.closingEquityAccountCode().value()));
    rows.add(
        List.of(
            "Closed totals", CliQueryOutputFormatter.joinedBalances(closedPeriod.closedTotals())));
    rows.add(List.of("Closed at", CliHumanDisplay.instant(closedPeriod.closedAt())));
    rows.add(
        List.of(
            "Closing postings",
            closedPeriod.closingPostingIds().isEmpty()
                ? "(none)"
                : closedPeriod.closingPostingIds().stream()
                    .map(dev.erst.fingrind.core.PostingId::value)
                    .collect(java.util.stream.Collectors.joining(", "))));
    if (closedPeriod.closedTotals().isEmpty() && closedPeriod.closingPostingIds().isEmpty()) {
      rows.add(
          List.of(
              "Outcome", "No closing movements were required for the selected reporting period."));
    }
    return CliTextFormat.renderTitledBlock(
        "Period Closed", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  static String renderPreflightAcceptedHuman(PostEntryResult.PreflightAccepted accepted) {
    return CliTextFormat.renderTitledBlock(
        "Entry Preflight Accepted",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Idempotency key", accepted.idempotencyKey().value()),
                List.of("Effective date", accepted.effectiveDate().toString()))));
  }

  static String renderCommittedHuman(PostEntryResult.Committed committed) {
    return CliTextFormat.renderTitledBlock(
        "Entry Committed",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Posting id", committed.postingId().value()),
                List.of("Idempotency key", committed.idempotencyKey().value()),
                List.of("Effective date", committed.effectiveDate().toString()),
                List.of("Recorded at", CliHumanDisplay.instant(committed.recordedAt())))));
  }

  private static String absolutePath(Path path) {
    return CliHumanDisplay.path(path);
  }

  private static String displayPassphraseSourceKind(BookAccess.PassphraseSource passphraseSource) {
    return switch (passphraseSource) {
      case BookAccess.PassphraseSource.KeyFile _ -> "Key file";
      case BookAccess.PassphraseSource.StandardInput _ -> "Standard input";
      case BookAccess.PassphraseSource.InteractivePrompt _ -> "Interactive prompt";
    };
  }
}
