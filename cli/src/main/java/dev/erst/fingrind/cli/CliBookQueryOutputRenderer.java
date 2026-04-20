package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Renders non-report query payloads such as inspection, accounts, postings, and balances. */
final class CliBookQueryOutputRenderer {
  private CliBookQueryOutputRenderer() {}

  static String renderBookInspectionHuman(Path bookFilePath, BookInspection inspection) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Book file", CliQueryOutputSupport.absolutePath(bookFilePath)));
    rows.add(List.of("State", inspection.status().wireValue()));
    rows.add(List.of("Initialized", Boolean.toString(inspection.initialized())));
    rows.add(
        List.of(
            "Compatible with current binary",
            Boolean.toString(inspection.compatibleWithCurrentBinary())));
    rows.add(
        List.of(
            CliOperationText.initializeWithOpenBookLabel(),
            Boolean.toString(inspection.canInitializeWithOpenBook())));
    rows.add(
        List.of(
            "Supported book format version",
            Integer.toString(inspection.supportedBookFormatVersion())));
    rows.add(List.of("Migration policy", inspection.migrationPolicy().wireValue()));
    if (inspection instanceof BookInspection.Existing existing) {
      rows.add(List.of("SQLite applicationId", Integer.toString(existing.applicationId())));
      rows.add(
          List.of(
              "Detected book format version",
              Integer.toString(existing.detectedBookFormatVersion())));
    }
    if (inspection instanceof BookInspection.Initialized initialized) {
      rows.add(List.of("SQLite applicationId", Integer.toString(initialized.applicationId())));
      rows.add(
          List.of(
              "Detected book format version",
              Integer.toString(initialized.detectedBookFormatVersion())));
      rows.add(List.of("Initialized at", initialized.initializedAt().toString()));
    }
    return CliTextFormat.renderTitledBlock(
        "Book Inspection", CliTextFormat.renderKeyValueBlock(rows));
  }

  static String renderAccountsHuman(AccountPage page) {
    String summary =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Returned accounts", Integer.toString(page.accounts().size())),
                List.of("Limit", Integer.toString(page.limit())),
                List.of("Offset", Integer.toString(page.offset())),
                List.of("More pages", Boolean.toString(page.hasMore()))));
    String table =
        CliTextFormat.renderTable(
            List.of("Account", "Name", "Normal balance", "Active", "Declared at"),
            page.accounts().stream()
                .map(
                    account ->
                        List.of(
                            account.accountCode().value(),
                            account.accountName().value(),
                            account.normalBalance().wireValue(),
                            Boolean.toString(account.active()),
                            account.declaredAt().toString()))
                .toList());
    return CliTextFormat.renderTitledBlock(
        "Accounts", summary + System.lineSeparator() + System.lineSeparator() + table);
  }

  static String renderAccountsCsv(AccountPage page) {
    return CliTextFormat.renderCsv(
        List.of("accountCode", "accountName", "normalBalance", "active", "declaredAt"),
        page.accounts().stream()
            .map(
                account ->
                    List.of(
                        account.accountCode().value(),
                        account.accountName().value(),
                        account.normalBalance().wireValue(),
                        Boolean.toString(account.active()),
                        account.declaredAt().toString()))
            .toList());
  }

  static String renderPostingHuman(PostingFact postingFact) {
    List<List<String>> header = new ArrayList<>();
    header.add(List.of("Posting id", postingFact.postingId().value()));
    header.add(List.of("Effective date", postingFact.journalEntry().effectiveDate().toString()));
    header.add(List.of("Recorded at", postingFact.provenance().recordedAt().toString()));
    header.add(List.of("Actor id", postingFact.provenance().requestProvenance().actorId().value()));
    header.add(
        List.of(
            "Actor type", postingFact.provenance().requestProvenance().actorType().wireValue()));
    header.add(
        List.of("Command id", postingFact.provenance().requestProvenance().commandId().value()));
    header.add(
        List.of(
            "Idempotency key",
            postingFact.provenance().requestProvenance().idempotencyKey().value()));
    header.add(
        List.of(
            "Causation id", postingFact.provenance().requestProvenance().causationId().value()));
    header.add(
        List.of(
            "Correlation id",
            postingFact
                .provenance()
                .requestProvenance()
                .correlationId()
                .map(value -> value.value())
                .orElse("(none)")));
    header.add(List.of("Source channel", postingFact.provenance().sourceChannel().wireValue()));
    header.add(
        List.of(
            "Reversal target",
            postingFact
                .reversalReference()
                .map(reference -> reference.priorPostingId().value())
                .orElse("(direct)")));
    header.add(
        List.of(
            "Reversal reason",
            postingFact.reversalReason().map(reason -> reason.value()).orElse("(none)")));
    String journalLines =
        CliTextFormat.renderTable(
            List.of("Account", "Side", "Currency", "Amount"),
            postingFact.journalEntry().lines().stream()
                .map(
                    line ->
                        List.of(
                            line.accountCode().value(),
                            line.side().wireValue(),
                            line.amount().currencyCode().value(),
                            CliTextFormat.displayAmount(
                                line.amount().currencyCode().value(), line.amount().amount())))
                .toList(),
            3);
    return CliTextFormat.renderTitledBlock(
        "Posting",
        CliTextFormat.renderKeyValueBlock(header)
            + System.lineSeparator()
            + System.lineSeparator()
            + journalLines);
  }

  static String renderPostingRegisterHuman(PostingPage page) {
    String summary =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Returned postings", Integer.toString(page.postings().size())),
                List.of("Limit", Integer.toString(page.limit())),
                List.of(
                    "Next cursor",
                    page.nextCursor().map(cursor -> cursor.wireValue()).orElse("(none)"))));
    String table =
        CliTextFormat.renderTable(
            List.of(
                "Effective date",
                "Recorded at",
                "Posting id",
                "Currency",
                "Total amount",
                "Accounts",
                "Reversal target"),
            page.postings().stream().map(CliQueryOutputSupport::postingRegisterRow).toList(),
            4);
    return CliTextFormat.renderTitledBlock(
        "Postings", summary + System.lineSeparator() + System.lineSeparator() + table);
  }

  static String renderPostingRegisterCsv(PostingPage page) {
    return CliTextFormat.renderCsv(
        List.of(
            "effectiveDate",
            "recordedAt",
            "postingId",
            "currencyCode",
            "totalAmount",
            "accountCodes",
            "reversalTarget"),
        page.postings().stream().map(CliQueryOutputSupport::postingRegisterRow).toList());
  }

  static String renderAccountBalanceHuman(AccountBalanceSnapshot snapshot) {
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Account", snapshot.account().accountCode().value()),
                List.of("Name", snapshot.account().accountName().value()),
                List.of("Normal balance", snapshot.account().normalBalance().wireValue()),
                List.of("Active", Boolean.toString(snapshot.account().active())),
                List.of(
                    "Range",
                    CliQueryOutputSupport.dateRange(
                        snapshot.effectiveDateFrom(), snapshot.effectiveDateTo()))));
    String balances =
        CliTextFormat.renderTable(
            List.of("Currency", "Debit total", "Credit total", "Net amount", "Balance side"),
            snapshot.balances().stream().map(CliQueryOutputSupport::balanceRow).toList(),
            1,
            2,
            3);
    return CliTextFormat.renderTitledBlock(
        "Account Balance", header + System.lineSeparator() + System.lineSeparator() + balances);
  }

  static String renderAccountBalanceCsv(AccountBalanceSnapshot snapshot) {
    return CliTextFormat.renderCsv(
        List.of(
            "accountCode",
            "accountName",
            "normalBalance",
            "effectiveDateFrom",
            "effectiveDateTo",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "netAmount",
            "balanceSide"),
        snapshot.balances().stream()
            .map(
                balance ->
                    List.of(
                        snapshot.account().accountCode().value(),
                        snapshot.account().accountName().value(),
                        snapshot.account().normalBalance().wireValue(),
                        snapshot.effectiveDateFrom().map(LocalDate::toString).orElse(""),
                        snapshot.effectiveDateTo().map(LocalDate::toString).orElse(""),
                        balance.netAmount().currencyCode().value(),
                        CliQueryOutputSupport.displayMoney(balance.debitTotal()),
                        CliQueryOutputSupport.displayMoney(balance.creditTotal()),
                        CliQueryOutputSupport.displayMoney(balance.netAmount()),
                        balance.balanceSide().wireValue()))
            .toList());
  }
}
