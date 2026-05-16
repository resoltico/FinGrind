package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.ClosedPeriod;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
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

  static String renderRekeyBookHuman(RekeyBookResult.Rekeyed rekeyed) {
    return CliTextFormat.renderTitledBlock(
        "Book Rekeyed",
        CliTextFormat.renderKeyValueBlock(
            List.of(List.of("Book file", absolutePath(rekeyed.bookFilePath())))));
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
    return CliTextFormat.renderTitledBlock(
        "Period Closed",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Close order", Integer.toString(closedPeriod.closeOrder())),
                List.of(
                    "Effective date range",
                    closedPeriod.reportingPeriod().effectiveDateFrom()
                        + " to "
                        + closedPeriod.reportingPeriod().effectiveDateTo()),
                List.of("Closing equity account", closedPeriod.closingEquityAccountCode().value()),
                List.of(
                    "Closed totals",
                    CliQueryOutputFormatter.joinedBalances(closedPeriod.closedTotals())),
                List.of("Closed at", CliHumanDisplay.instant(closedPeriod.closedAt())),
                List.of(
                    "Closing postings",
                    closedPeriod.closingPostingIds().stream()
                        .map(dev.erst.fingrind.core.PostingId::value)
                        .map(CliHumanDisplay::compactOpaqueIdentifier)
                        .collect(java.util.stream.Collectors.joining(", "))))));
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
                List.of(
                    "Posting id",
                    CliHumanDisplay.compactOpaqueIdentifier(committed.postingId().value())),
                List.of(
                    "Idempotency key",
                    CliHumanDisplay.compactOpaqueIdentifier(committed.idempotencyKey().value())),
                List.of("Effective date", committed.effectiveDate().toString()),
                List.of("Recorded at", CliHumanDisplay.instant(committed.recordedAt())))));
  }

  private static String absolutePath(Path path) {
    return CliHumanDisplay.path(path);
  }
}
