package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.TrialBalanceReport;
import java.time.LocalDate;
import java.util.List;

/** Renders semantic reporting payloads such as trial balances, ledgers, and period summaries. */
final class CliReportOutputRenderer {
  private CliReportOutputRenderer() {}

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
            report.rows().stream().map(CliQueryOutputFormatter::trialBalanceRow).toList(),
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
                        row.balance().netAmount().currencyUnit().code(),
                        CliQueryOutputFormatter.displayMoney(row.balance().debitTotal()),
                        CliQueryOutputFormatter.displayMoney(row.balance().creditTotal()),
                        CliQueryOutputFormatter.displayMoney(row.balance().netAmount()),
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
                    CliQueryOutputFormatter.dateRange(
                        report.effectiveDateRange().effectiveDateFrom().orElse(null),
                        report.effectiveDateRange().effectiveDateTo().orElse(null))),
                List.of(
                    "Opening balances",
                    CliQueryOutputFormatter.joinedBalances(report.openingBalances())),
                List.of(
                    "Closing balances",
                    CliQueryOutputFormatter.joinedBalances(report.closingBalances()))));
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
                .map(entry -> CliQueryOutputFormatter.accountLedgerRow(report.account(), entry))
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
                        entry.movement().netAmount().currencyUnit().code(),
                        CliQueryOutputFormatter.displayMoney(entry.movement().debitTotal()),
                        CliQueryOutputFormatter.displayMoney(entry.movement().creditTotal()),
                        CliQueryOutputFormatter.displayMoney(entry.runningNetAmount()),
                        entry.runningBalanceSide().wireValue(),
                        CliQueryOutputFormatter.counterpartAccounts(
                            report.account(), entry.postingFact())))
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
            report.currencyTotals().stream()
                .map(summary -> CliQueryOutputFormatter.balanceRow(summary.totals()))
                .toList(),
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
                .map(CliQueryOutputFormatter::periodActivityRow)
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
                        row.movement().netAmount().currencyUnit().code(),
                        CliQueryOutputFormatter.displayMoney(row.movement().debitTotal()),
                        CliQueryOutputFormatter.displayMoney(row.movement().creditTotal()),
                        CliQueryOutputFormatter.displayMoney(row.movement().netAmount()),
                        row.movement().balanceSide().wireValue()))
            .toList());
  }
}
