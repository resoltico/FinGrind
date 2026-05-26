package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.bookkeeping.TransferredPeriodResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.sqlite.secret.SqliteBookKeyFileGenerator;
import java.nio.file.Path;
import java.util.List;

/** Shared plain-language rendering for administrative and write command successes. */
final class CliMutationOutputRenderer {
  private CliMutationOutputRenderer() {}

  static String renderGeneratedBookKeyFileText(
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

  static String renderOpenBookText(Path bookFilePath, OpenBookResult.Opened opened) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Book file", absolutePath(bookFilePath)));
    rows.addAll(CliBookIdentityDisplay.rows(opened.bookIdentity()));
    rows.add(List.of("Initialized at", CliTextDisplay.instant(opened.initializedAt())));
    return CliTextFormat.renderTitledBlock(
        "Book Initialized", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  static String renderRekeyBookText(
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

  static String renderBackupBookText(BackupBookResult.BackedUp backedUp) {
    return CliTextFormat.renderTitledBlock(
        "Book Backed Up",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", absolutePath(backedUp.bookFilePath())),
                List.of("Backup file", absolutePath(backedUp.backupFilePath())),
                List.of("Backup key file", absolutePath(backedUp.backupBookKeyFilePath())))));
  }

  static String renderRestoreBookText(RestoreBookResult.Restored restored) {
    return CliTextFormat.renderTitledBlock(
        "Book Restored",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", absolutePath(restored.bookFilePath())),
                List.of("Backup file", absolutePath(restored.backupFilePath())),
                List.of("Book key file", absolutePath(restored.backupBookKeyFilePath())))));
  }

  static String renderInspectRekeyRollbackText(RekeyRollbackResult.Inspected inspected) {
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

  static String renderRestoreRekeyRollbackText(RekeyRollbackResult.Restored restored) {
    return CliTextFormat.renderTitledBlock(
        "Book Restored From Rollback",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", absolutePath(restored.bookFilePath())),
                List.of("Rollback artifact", absolutePath(restored.rollbackArtifactPath())))));
  }

  static String renderDeleteRekeyRollbackText(RekeyRollbackResult.Deleted deleted) {
    return CliTextFormat.renderTitledBlock(
        "Rollback Artifact Deleted",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", absolutePath(deleted.bookFilePath())),
                List.of("Rollback artifact", absolutePath(deleted.rollbackArtifactPath())))));
  }

  static String renderDeclaredAccountText(DeclaredAccount account) {
    return CliTextFormat.renderTitledBlock(
        "Account Declared",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Account code", account.accountCode().value()),
                List.of("Account name", account.accountName().value()),
                List.of(
                    "Parent account",
                    account
                        .accountTaxonomy()
                        .parentAccountCode()
                        .map(parent -> parent.value())
                        .orElse("(none)")),
                List.of(
                    "Account type",
                    CliQueryOutputFormatter.displayLineTypeLabel(account.accountType())),
                List.of(
                    "Account role",
                    CliQueryOutputFormatter.displayAccountRoleLabel(account.accountRole())),
                List.of(
                    "Financial-position line",
                    account
                        .accountTaxonomy()
                        .financialPositionLineClassification()
                        .map(CliQueryOutputFormatter::displayFinancialPositionLineClassification)
                        .orElse("(none)")),
                List.of(
                    "Profit-and-loss line",
                    account
                        .accountTaxonomy()
                        .profitAndLossLineClassification()
                        .map(CliQueryOutputFormatter::displayProfitAndLossLineClassification)
                        .orElse("(none)")),
                List.of(
                    "Normal balance",
                    CliQueryOutputFormatter.displayNormalBalanceLabel(account.normalBalance())),
                List.of("Active", CliQueryOutputFormatter.displayBooleanLabel(account.active())),
                List.of("Declared at", CliTextDisplay.instant(account.declaredAt())))));
  }

  static String renderTransferredPeriodResultText(TransferredPeriodResult transferredPeriodResult) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Transfer order", Integer.toString(transferredPeriodResult.transferOrder())));
    rows.add(
        List.of(
            "Effective date range",
            transferredPeriodResult.reportingPeriod().effectiveDateFrom()
                + " to "
                + transferredPeriodResult.reportingPeriod().effectiveDateTo()));
    rows.add(
        List.of(
            "Result-holding account", transferredPeriodResult.resultHoldingAccountCode().value()));
    rows.add(
        List.of(
            "Transferred totals",
            CliQueryOutputFormatter.joinedBalances(transferredPeriodResult.transferredTotals())));
    rows.add(
        List.of("Transferred at", CliTextDisplay.instant(transferredPeriodResult.transferredAt())));
    rows.add(
        List.of(
            "Closing postings",
            transferredPeriodResult.transferPostingIds().isEmpty()
                ? "(none)"
                : transferredPeriodResult.transferPostingIds().stream()
                    .map(dev.erst.fingrind.core.PostingId::value)
                    .collect(java.util.stream.Collectors.joining(", "))));
    if (transferredPeriodResult.transferredTotals().isEmpty()
        && transferredPeriodResult.transferPostingIds().isEmpty()) {
      rows.add(
          List.of(
              "Outcome", "No closing movements were required for the selected reporting period."));
    }
    return CliTextFormat.renderTitledBlock(
        "Period Closed", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  static String renderPreflightAcceptedText(PostEntryResult.PreflightAccepted accepted) {
    return CliTextFormat.renderTitledBlock(
        "Entry Preflight Accepted",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Idempotency key", accepted.idempotencyKey().value()),
                List.of("Effective date", accepted.effectiveDate().toString()))));
  }

  static String renderCommittedText(PostEntryResult.Committed committed) {
    return CliTextFormat.renderTitledBlock(
        "Entry Committed",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Posting id", committed.postingId().value()),
                List.of("Idempotency key", committed.idempotencyKey().value()),
                List.of("Effective date", committed.effectiveDate().toString()),
                List.of("Recorded at", CliTextDisplay.instant(committed.recordedAt())))));
  }

  private static String absolutePath(Path path) {
    return CliTextDisplay.path(path);
  }

  private static String absolutePath(PublicPathHint pathHint) {
    return CliTextDisplay.path(pathHint);
  }

  private static String displayPassphraseSourceKind(BookAccess.PassphraseSource passphraseSource) {
    return switch (passphraseSource) {
      case BookAccess.PassphraseSource.KeyFile _ -> "Key file";
      case BookAccess.PassphraseSource.StandardInput _ -> "Standard input";
      case BookAccess.PassphraseSource.InteractivePrompt _ -> "Interactive prompt";
    };
  }
}
