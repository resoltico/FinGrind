package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.TrialBalanceRow;
import dev.erst.fingrind.core.JournalLine;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Renders successful query and report payloads in human and CSV forms. */
final class CliQueryOutputRenderer {
  private CliQueryOutputRenderer() {}

  static String renderBookInspectionHuman(Path bookFilePath, BookInspection inspection) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Book file", absolutePath(bookFilePath)));
    rows.add(List.of("State", inspection.status().wireValue()));
    rows.add(List.of("Initialized", Boolean.toString(inspection.initialized())));
    rows.add(
        List.of(
            "Compatible with current binary",
            Boolean.toString(inspection.compatibleWithCurrentBinary())));
    rows.add(
        List.of(
            "Can initialize with open-book",
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
            page.postings().stream().map(CliQueryOutputRenderer::postingRegisterRow).toList(),
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
        page.postings().stream().map(CliQueryOutputRenderer::postingRegisterRow).toList());
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
                    "Range", dateRange(snapshot.effectiveDateFrom(), snapshot.effectiveDateTo()))));
    String balances =
        CliTextFormat.renderTable(
            List.of("Currency", "Debit total", "Credit total", "Net amount", "Balance side"),
            snapshot.balances().stream().map(CliQueryOutputRenderer::balanceRow).toList(),
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
                        displayMoney(balance.debitTotal()),
                        displayMoney(balance.creditTotal()),
                        displayMoney(balance.netAmount()),
                        balance.balanceSide().wireValue()))
            .toList());
  }

  static String renderTrialBalanceHuman(TrialBalanceReport report) {
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Effective date to",
                    report.effectiveDateTo().map(LocalDate::toString).orElse("(current)"))));
    String table =
        CliTextFormat.renderTable(
            List.of(
                "Account",
                "Name",
                "Normal balance",
                "Active",
                "Currency",
                "Debit total",
                "Credit total",
                "Net amount",
                "Balance side"),
            report.rows().stream().map(CliQueryOutputRenderer::trialBalanceRow).toList(),
            5,
            6,
            7);
    return CliTextFormat.renderTitledBlock(
        "Trial Balance", header + System.lineSeparator() + System.lineSeparator() + table);
  }

  static String renderTrialBalanceCsv(TrialBalanceReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "effectiveDateTo",
            "accountCode",
            "accountName",
            "normalBalance",
            "active",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "netAmount",
            "balanceSide"),
        report.rows().stream()
            .map(
                row ->
                    List.of(
                        report.effectiveDateTo().map(LocalDate::toString).orElse(""),
                        row.account().accountCode().value(),
                        row.account().accountName().value(),
                        row.account().normalBalance().wireValue(),
                        Boolean.toString(row.account().active()),
                        row.balance().netAmount().currencyCode().value(),
                        displayMoney(row.balance().debitTotal()),
                        displayMoney(row.balance().creditTotal()),
                        displayMoney(row.balance().netAmount()),
                        row.balance().balanceSide().wireValue()))
            .toList());
  }

  static String renderAccountLedgerHuman(AccountLedgerReport report) {
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Account", report.account().accountCode().value()),
                List.of("Name", report.account().accountName().value()),
                List.of("Normal balance", report.account().normalBalance().wireValue()),
                List.of(
                    "Range",
                    dateRange(
                        report.effectiveDateRange().effectiveDateFrom(),
                        report.effectiveDateRange().effectiveDateTo())),
                List.of("Opening balances", joinedBalances(report.openingBalances())),
                List.of("Closing balances", joinedBalances(report.closingBalances()))));
    String table =
        CliTextFormat.renderTable(
            List.of(
                "Effective date",
                "Recorded at",
                "Posting id",
                "Currency",
                "Debit",
                "Credit",
                "Running balance",
                "Balance side",
                "Counterpart accounts"),
            report.entries().stream()
                .map(entry -> accountLedgerRow(report.account(), entry))
                .toList(),
            4,
            5,
            6);
    return CliTextFormat.renderTitledBlock(
        "Account Ledger", header + System.lineSeparator() + System.lineSeparator() + table);
  }

  static String renderAccountLedgerCsv(AccountLedgerReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "accountCode",
            "accountName",
            "effectiveDateFrom",
            "effectiveDateTo",
            "postingId",
            "effectiveDate",
            "recordedAt",
            "currencyCode",
            "debitAmount",
            "creditAmount",
            "runningBalance",
            "runningBalanceSide",
            "counterpartAccounts"),
        report.entries().stream()
            .map(
                entry ->
                    List.of(
                        report.account().accountCode().value(),
                        report.account().accountName().value(),
                        report
                            .effectiveDateRange()
                            .effectiveDateFrom()
                            .map(LocalDate::toString)
                            .orElse(""),
                        report
                            .effectiveDateRange()
                            .effectiveDateTo()
                            .map(LocalDate::toString)
                            .orElse(""),
                        entry.postingFact().postingId().value(),
                        entry.postingFact().journalEntry().effectiveDate().toString(),
                        entry.postingFact().provenance().recordedAt().toString(),
                        entry.movement().netAmount().currencyCode().value(),
                        displayMoney(entry.movement().debitTotal()),
                        displayMoney(entry.movement().creditTotal()),
                        displayMoney(entry.runningNetAmount()),
                        entry.runningBalanceSide().wireValue(),
                        counterpartAccounts(report.account(), entry.postingFact())))
            .toList());
  }

  static String renderPeriodSummaryHuman(PeriodSummaryReport report) {
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Range", report.effectiveDateFrom() + " to " + report.effectiveDateTo()),
                List.of("Posting count", Integer.toString(report.postingCount())),
                List.of("Posting line count", Integer.toString(report.postingLineCount())),
                List.of("Accounts touched", Integer.toString(report.accountsTouched()))));
    String currencyTotals =
        CliTextFormat.renderTable(
            List.of("Currency", "Debit total", "Credit total", "Net amount", "Balance side"),
            report.currencyTotals().stream().map(summary -> balanceRow(summary.totals())).toList(),
            1,
            2,
            3);
    String accountActivity =
        CliTextFormat.renderTable(
            List.of(
                "Account",
                "Name",
                "Normal balance",
                "Currency",
                "Debit total",
                "Credit total",
                "Net amount",
                "Balance side"),
            report.accountActivity().stream()
                .map(CliQueryOutputRenderer::periodActivityRow)
                .toList(),
            4,
            5,
            6);
    return CliTextFormat.renderTitledBlock(
        "Period Summary",
        header
            + System.lineSeparator()
            + System.lineSeparator()
            + currencyTotals
            + System.lineSeparator()
            + System.lineSeparator()
            + accountActivity);
  }

  static String renderPeriodSummaryCsv(PeriodSummaryReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "effectiveDateFrom",
            "effectiveDateTo",
            "postingCount",
            "postingLineCount",
            "accountsTouched",
            "accountCode",
            "accountName",
            "normalBalance",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "netAmount",
            "balanceSide"),
        report.accountActivity().stream()
            .map(
                row ->
                    List.of(
                        report.effectiveDateFrom().toString(),
                        report.effectiveDateTo().toString(),
                        Integer.toString(report.postingCount()),
                        Integer.toString(report.postingLineCount()),
                        Integer.toString(report.accountsTouched()),
                        row.account().accountCode().value(),
                        row.account().accountName().value(),
                        row.account().normalBalance().wireValue(),
                        row.movement().netAmount().currencyCode().value(),
                        displayMoney(row.movement().debitTotal()),
                        displayMoney(row.movement().creditTotal()),
                        displayMoney(row.movement().netAmount()),
                        row.movement().balanceSide().wireValue()))
            .toList());
  }

  private static List<String> postingRegisterRow(PostingFact postingFact) {
    return List.of(
        postingFact.journalEntry().effectiveDate().toString(),
        postingFact.provenance().recordedAt().toString(),
        postingFact.postingId().value(),
        postingCurrency(postingFact),
        postingTotalAmount(postingFact),
        postingAccounts(postingFact),
        postingFact
            .reversalReference()
            .map(reference -> reference.priorPostingId().value())
            .orElse(""));
  }

  private static List<String> balanceRow(CurrencyBalance balance) {
    return List.of(
        balance.netAmount().currencyCode().value(),
        displayMoney(balance.debitTotal()),
        displayMoney(balance.creditTotal()),
        displayMoney(balance.netAmount()),
        balance.balanceSide().wireValue());
  }

  private static List<String> trialBalanceRow(TrialBalanceRow row) {
    return List.of(
        row.account().accountCode().value(),
        row.account().accountName().value(),
        row.account().normalBalance().wireValue(),
        Boolean.toString(row.account().active()),
        row.balance().netAmount().currencyCode().value(),
        displayMoney(row.balance().debitTotal()),
        displayMoney(row.balance().creditTotal()),
        displayMoney(row.balance().netAmount()),
        row.balance().balanceSide().wireValue());
  }

  private static List<String> accountLedgerRow(DeclaredAccount account, AccountLedgerEntry entry) {
    return List.of(
        entry.postingFact().journalEntry().effectiveDate().toString(),
        entry.postingFact().provenance().recordedAt().toString(),
        entry.postingFact().postingId().value(),
        entry.movement().netAmount().currencyCode().value(),
        displayMoney(entry.movement().debitTotal()),
        displayMoney(entry.movement().creditTotal()),
        displayMoney(entry.runningNetAmount()),
        entry.runningBalanceSide().wireValue(),
        counterpartAccounts(account, entry.postingFact()));
  }

  private static List<String> periodActivityRow(PeriodAccountActivityRow row) {
    return List.of(
        row.account().accountCode().value(),
        row.account().accountName().value(),
        row.account().normalBalance().wireValue(),
        row.movement().netAmount().currencyCode().value(),
        displayMoney(row.movement().debitTotal()),
        displayMoney(row.movement().creditTotal()),
        displayMoney(row.movement().netAmount()),
        row.movement().balanceSide().wireValue());
  }

  private static String counterpartAccounts(DeclaredAccount account, PostingFact postingFact) {
    List<String> counterparts =
        postingFact.journalEntry().lines().stream()
            .map(JournalLine::accountCode)
            .map(accountCode -> accountCode.value())
            .filter(accountCode -> !accountCode.equals(account.accountCode().value()))
            .distinct()
            .toList();
    return counterparts.isEmpty() ? "(self)" : CliTextFormat.joined(counterparts);
  }

  private static String postingAccounts(PostingFact postingFact) {
    return CliTextFormat.joined(
        postingFact.journalEntry().lines().stream()
            .map(line -> line.accountCode().value())
            .distinct()
            .toList());
  }

  private static String postingCurrency(PostingFact postingFact) {
    return postingFact.journalEntry().lines().getFirst().amount().currencyCode().value();
  }

  private static String postingTotalAmount(PostingFact postingFact) {
    JournalLine firstLine = postingFact.journalEntry().lines().getFirst();
    java.math.BigDecimal debitTotal =
        postingFact.journalEntry().lines().stream()
            .filter(line -> line.side() == JournalLine.EntrySide.DEBIT)
            .map(line -> line.amount().amount())
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    return CliTextFormat.displayAmount(firstLine.amount().currencyCode().value(), debitTotal);
  }

  private static String joinedBalances(List<CurrencyBalance> balances) {
    if (balances.isEmpty()) {
      return "(none)";
    }
    return balances.stream()
        .map(
            balance ->
                balance.netAmount().currencyCode().value()
                    + " "
                    + displayMoney(balance.netAmount())
                    + " "
                    + balance.balanceSide().wireValue())
        .collect(Collectors.joining(", "));
  }

  private static String displayMoney(dev.erst.fingrind.core.Money money) {
    return CliTextFormat.displayAmount(money.currencyCode().value(), money.amount());
  }

  private static String dateRange(
      Optional<LocalDate> effectiveDateFrom, Optional<LocalDate> effectiveDateTo) {
    return effectiveDateFrom.map(LocalDate::toString).orElse("(start)")
        + " to "
        + effectiveDateTo.map(LocalDate::toString).orElse("(current)");
  }

  private static String absolutePath(Path bookFilePath) {
    return bookFilePath.toAbsolutePath().normalize().toString();
  }
}
