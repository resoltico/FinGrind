package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;

/** Renders semantic reporting payloads such as trial balances, ledgers, and period summaries. */
final class CliReportOutputRenderer {
  private CliReportOutputRenderer() {}

  static String renderTrialBalanceHuman(TrialBalanceReport report) {
    String header =
        CliTextFormat.renderKeyValueBlock(
            statementIdentityRows(
                report.bookIdentity(),
                report.postingCoverage(),
                List.of(
                    List.of(
                        "Effective date to",
                        report.effectiveDateTo().map(LocalDate::toString).orElse("(current)")))));
    String table =
        CliTextFormat.renderTable(
            List.of(
                "Account",
                "Name",
                "Account type",
                "Account role",
                "Normal balance",
                "Active",
                "Currency",
                "Debit total",
                "Credit total",
                "Net amount",
                "Balance side"),
            report.rows().stream().map(CliQueryOutputFormatter::trialBalanceHumanRow).toList(),
            6,
            7,
            8);
    return CliTextFormat.renderTitledBlock(
        "Trial Balance", header + System.lineSeparator() + System.lineSeparator() + table);
  }

  static String renderTrialBalanceCsv(TrialBalanceReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "entityName",
            "functionalCurrency",
            "fiscalYearStart",
            "effectiveDateTo",
            "postingCoverage",
            "accountCode",
            "accountName",
            "accountType",
            "accountRole",
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
                        report.bookIdentity().entityName().value(),
                        report.bookIdentity().functionalCurrency().code(),
                        report.bookIdentity().fiscalYearStart().wireValue(),
                        report.effectiveDateTo().map(LocalDate::toString).orElse(""),
                        report.postingCoverage().wireValue(),
                        row.account().accountCode().value(),
                        row.account().accountName().value(),
                        row.account().accountType().wireValue(),
                        row.account().accountRole().wireValue(),
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
                List.of("Account type", report.account().accountType().wireValue()),
                List.of("Account role", report.account().accountRole().wireValue()),
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
                .map(
                    entry -> CliQueryOutputFormatter.accountLedgerHumanRow(report.account(), entry))
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
            "accountType",
            "accountRole",
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
                        report.account().accountType().wireValue(),
                        report.account().accountRole().wireValue(),
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
                .map(summary -> CliQueryOutputFormatter.balanceHumanRow(summary.totals()))
                .toList(),
            1,
            2,
            3);
    String accountActivity =
        CliTextFormat.renderTable(
            List.of(
                "Account",
                "Name",
                "Account type",
                "Account role",
                "Normal balance",
                "Currency",
                "Debit total",
                "Credit total",
                "Net amount",
                "Balance side"),
            report.accountActivity().stream()
                .map(CliQueryOutputFormatter::periodActivityHumanRow)
                .toList(),
            5,
            6,
            7);
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
            "accountType",
            "accountRole",
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
                        row.account().accountType().wireValue(),
                        row.account().accountRole().wireValue(),
                        row.account().normalBalance().wireValue(),
                        row.movement().netAmount().currencyUnit().code(),
                        CliQueryOutputFormatter.displayMoney(row.movement().debitTotal()),
                        CliQueryOutputFormatter.displayMoney(row.movement().creditTotal()),
                        CliQueryOutputFormatter.displayMoney(row.movement().netAmount()),
                        row.movement().balanceSide().wireValue()))
            .toList());
  }

  static String renderFinancialPositionHuman(FinancialPositionReport report) {
    String header =
        CliTextFormat.renderKeyValueBlock(
            statementIdentityRows(
                report.bookIdentity(),
                report.postingCoverage(),
                List.of(
                    List.of(
                        "Effective date to",
                        report.effectiveDateTo().map(LocalDate::toString).orElse("(current)")))));
    String sections =
        report.sections().isEmpty()
            ? "(none)"
            : report.sections().stream()
                .map(
                    section ->
                        renderStatementSection(
                            CliQueryOutputFormatter.displayAccountTypeSectionLabel(
                                section.accountType()),
                            CliTextFormat.renderTable(
                                List.of(
                                    "Line code",
                                    "Line name",
                                    "Role",
                                    "Kind",
                                    "Currency",
                                    "Debit total",
                                    "Credit total",
                                    "Net amount",
                                    "Balance side"),
                                section.rows().stream()
                                    .map(
                                        row ->
                                            List.of(
                                                row.lineCode(),
                                                row.lineName(),
                                                CliQueryOutputFormatter.displayLineRole(
                                                    row.lineRole()),
                                                CliQueryOutputFormatter.displayRowKind(
                                                    row.synthetic()),
                                                row.balance().netAmount().currencyUnit().code(),
                                                CliQueryOutputFormatter.displayMoney(
                                                    row.balance().debitTotal()),
                                                CliQueryOutputFormatter.displayMoney(
                                                    row.balance().creditTotal()),
                                                CliQueryOutputFormatter.displayMoney(
                                                    row.balance().netAmount()),
                                                CliQueryOutputFormatter.displayBalanceSideLabel(
                                                    row.balance().balanceSide())))
                                    .toList(),
                                4,
                                5,
                                6),
                            section.totals()))
                .collect(
                    java.util.stream.Collectors.joining(
                        System.lineSeparator() + System.lineSeparator()));
    return CliTextFormat.renderTitledBlock(
        "Financial Position", header + System.lineSeparator() + System.lineSeparator() + sections);
  }

  static String renderFinancialPositionCsv(FinancialPositionReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "entityName",
            "functionalCurrency",
            "fiscalYearStart",
            "effectiveDateTo",
            "postingCoverage",
            "sectionAccountType",
            "lineCode",
            "lineName",
            "lineRole",
            "lineType",
            "synthetic",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "netAmount",
            "balanceSide"),
        report.sections().stream()
            .flatMap(
                section ->
                    section.rows().stream()
                        .map(
                            row ->
                                List.of(
                                    report.bookIdentity().entityName().value(),
                                    report.bookIdentity().functionalCurrency().code(),
                                    report.bookIdentity().fiscalYearStart().wireValue(),
                                    report.effectiveDateTo().map(LocalDate::toString).orElse(""),
                                    report.postingCoverage().wireValue(),
                                    section.accountType().wireValue(),
                                    row.lineCode(),
                                    row.lineName(),
                                    row.lineRole()
                                        .map(dev.erst.fingrind.core.AccountRole::wireValue)
                                        .orElse(""),
                                    row.lineType().wireValue(),
                                    Boolean.toString(row.synthetic()),
                                    row.balance().netAmount().currencyUnit().code(),
                                    CliQueryOutputFormatter.displayMoney(
                                        row.balance().debitTotal()),
                                    CliQueryOutputFormatter.displayMoney(
                                        row.balance().creditTotal()),
                                    CliQueryOutputFormatter.displayMoney(row.balance().netAmount()),
                                    row.balance().balanceSide().wireValue())))
            .toList());
  }

  static String renderIncomeStatementHuman(IncomeStatementReport report) {
    String header =
        CliTextFormat.renderKeyValueBlock(
            statementIdentityRows(
                report.bookIdentity(),
                report.postingCoverage(),
                List.of(
                    List.of("Effective date from", report.effectiveDateFrom().toString()),
                    List.of("Effective date to", report.effectiveDateTo().toString()),
                    List.of(
                        "Net income totals",
                        report.netIncomeTotals().isEmpty()
                            ? "(none)"
                            : report.netIncomeTotals().stream()
                                .map(CliQueryOutputFormatter::displayBalanceHuman)
                                .collect(java.util.stream.Collectors.joining(", "))))));
    String sections =
        report.sections().isEmpty()
            ? "(none)"
            : report.sections().stream()
                .map(
                    section ->
                        renderStatementSection(
                            CliQueryOutputFormatter.displayAccountTypeSectionLabel(
                                section.accountType()),
                            CliTextFormat.renderTable(
                                List.of(
                                    "Line code",
                                    "Line name",
                                    "Role",
                                    "Kind",
                                    "Currency",
                                    "Debit total",
                                    "Credit total",
                                    "Net amount",
                                    "Balance side"),
                                section.rows().stream()
                                    .map(
                                        row ->
                                            List.of(
                                                row.lineCode(),
                                                row.lineName(),
                                                CliQueryOutputFormatter.displayLineRole(
                                                    row.lineRole()),
                                                CliQueryOutputFormatter.displayRowKind(
                                                    row.synthetic()),
                                                row.movement().netAmount().currencyUnit().code(),
                                                CliQueryOutputFormatter.displayMoney(
                                                    row.movement().debitTotal()),
                                                CliQueryOutputFormatter.displayMoney(
                                                    row.movement().creditTotal()),
                                                CliQueryOutputFormatter.displayMoney(
                                                    row.movement().netAmount()),
                                                CliQueryOutputFormatter.displayBalanceSideLabel(
                                                    row.movement().balanceSide())))
                                    .toList(),
                                4,
                                5,
                                6),
                            section.totals()))
                .collect(
                    java.util.stream.Collectors.joining(
                        System.lineSeparator() + System.lineSeparator()));
    return CliTextFormat.renderTitledBlock(
        "Income Statement", header + System.lineSeparator() + System.lineSeparator() + sections);
  }

  static String renderIncomeStatementCsv(IncomeStatementReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "entityName",
            "functionalCurrency",
            "fiscalYearStart",
            "effectiveDateFrom",
            "effectiveDateTo",
            "postingCoverage",
            "sectionAccountType",
            "lineCode",
            "lineName",
            "lineRole",
            "lineType",
            "synthetic",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "netAmount",
            "balanceSide"),
        report.sections().stream()
            .flatMap(
                section ->
                    section.rows().stream()
                        .map(
                            row ->
                                List.of(
                                    report.bookIdentity().entityName().value(),
                                    report.bookIdentity().functionalCurrency().code(),
                                    report.bookIdentity().fiscalYearStart().wireValue(),
                                    report.effectiveDateFrom().toString(),
                                    report.effectiveDateTo().toString(),
                                    report.postingCoverage().wireValue(),
                                    section.accountType().wireValue(),
                                    row.lineCode(),
                                    row.lineName(),
                                    row.lineRole()
                                        .map(dev.erst.fingrind.core.AccountRole::wireValue)
                                        .orElse(""),
                                    row.lineType().wireValue(),
                                    Boolean.toString(row.synthetic()),
                                    row.movement().netAmount().currencyUnit().code(),
                                    CliQueryOutputFormatter.displayMoney(
                                        row.movement().debitTotal()),
                                    CliQueryOutputFormatter.displayMoney(
                                        row.movement().creditTotal()),
                                    CliQueryOutputFormatter.displayMoney(
                                        row.movement().netAmount()),
                                    row.movement().balanceSide().wireValue())))
            .toList());
  }

  static String renderChangesInEquityHuman(ChangesInEquityReport report) {
    String header =
        CliTextFormat.renderKeyValueBlock(
            statementIdentityRows(
                report.bookIdentity(),
                report.postingCoverage(),
                List.of(
                    List.of("Effective date from", report.effectiveDateFrom().toString()),
                    List.of("Effective date to", report.effectiveDateTo().toString()),
                    List.of("Opening totals", joinedBalancesHuman(report.openingTotals())),
                    List.of("Movement totals", joinedBalancesHuman(report.movementTotals())),
                    List.of("Closing totals", joinedBalancesHuman(report.closingTotals())))));
    String table =
        CliTextFormat.renderTable(
            List.of(
                "Line code",
                "Line name",
                "Role",
                "Kind",
                "Currency",
                "Opening",
                "Movement",
                "Closing",
                "Closing side"),
            report.rows().stream()
                .map(
                    row ->
                        List.of(
                            row.lineCode(),
                            row.lineName(),
                            CliQueryOutputFormatter.displayLineRole(row.lineRole()),
                            CliQueryOutputFormatter.displayRowKind(row.synthetic()),
                            row.closingBalance().netAmount().currencyUnit().code(),
                            CliQueryOutputFormatter.displayMoney(row.openingBalance().netAmount()),
                            CliQueryOutputFormatter.displayMoney(row.movement().netAmount()),
                            CliQueryOutputFormatter.displayMoney(row.closingBalance().netAmount()),
                            CliQueryOutputFormatter.displayBalanceSideLabel(
                                row.closingBalance().balanceSide())))
                .toList(),
            4,
            5,
            6);
    return CliTextFormat.renderTitledBlock(
        "Changes In Equity", header + System.lineSeparator() + System.lineSeparator() + table);
  }

  static String renderChangesInEquityCsv(ChangesInEquityReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "entityName",
            "functionalCurrency",
            "fiscalYearStart",
            "effectiveDateFrom",
            "effectiveDateTo",
            "postingCoverage",
            "lineCode",
            "lineName",
            "lineRole",
            "synthetic",
            "currencyCode",
            "openingDebitTotal",
            "openingCreditTotal",
            "openingNetAmount",
            "openingBalanceSide",
            "movementDebitTotal",
            "movementCreditTotal",
            "movementNetAmount",
            "movementBalanceSide",
            "closingDebitTotal",
            "closingCreditTotal",
            "closingNetAmount",
            "closingBalanceSide"),
        report.rows().stream()
            .map(
                row ->
                    List.of(
                        report.bookIdentity().entityName().value(),
                        report.bookIdentity().functionalCurrency().code(),
                        report.bookIdentity().fiscalYearStart().wireValue(),
                        report.effectiveDateFrom().toString(),
                        report.effectiveDateTo().toString(),
                        report.postingCoverage().wireValue(),
                        row.lineCode(),
                        row.lineName(),
                        row.lineRole()
                            .map(dev.erst.fingrind.core.AccountRole::wireValue)
                            .orElse(""),
                        Boolean.toString(row.synthetic()),
                        row.closingBalance().netAmount().currencyUnit().code(),
                        CliQueryOutputFormatter.displayMoney(row.openingBalance().debitTotal()),
                        CliQueryOutputFormatter.displayMoney(row.openingBalance().creditTotal()),
                        CliQueryOutputFormatter.displayMoney(row.openingBalance().netAmount()),
                        row.openingBalance().balanceSide().wireValue(),
                        CliQueryOutputFormatter.displayMoney(row.movement().debitTotal()),
                        CliQueryOutputFormatter.displayMoney(row.movement().creditTotal()),
                        CliQueryOutputFormatter.displayMoney(row.movement().netAmount()),
                        row.movement().balanceSide().wireValue(),
                        CliQueryOutputFormatter.displayMoney(row.closingBalance().debitTotal()),
                        CliQueryOutputFormatter.displayMoney(row.closingBalance().creditTotal()),
                        CliQueryOutputFormatter.displayMoney(row.closingBalance().netAmount()),
                        row.closingBalance().balanceSide().wireValue()))
            .toList());
  }

  private static String joinedBalancesHuman(List<dev.erst.fingrind.core.CurrencyBalance> balances) {
    if (balances.isEmpty()) {
      return "(none)";
    }
    return balances.stream()
        .map(CliQueryOutputFormatter::displayBalanceHuman)
        .collect(java.util.stream.Collectors.joining(", "));
  }

  private static String renderStatementSection(
      String title, String table, List<dev.erst.fingrind.core.CurrencyBalance> totals) {
    return title
        + System.lineSeparator()
        + "-".repeat(title.length())
        + System.lineSeparator()
        + table
        + System.lineSeparator()
        + System.lineSeparator()
        + CliTextFormat.renderKeyValueBlock(
            List.of(List.of("Section totals", joinedBalancesHuman(totals))));
  }

  private static List<List<String>> statementIdentityRows(
      BookIdentity bookIdentity, PostingCoverage postingCoverage, List<List<String>> rows) {
    List<List<String>> identityRows = new java.util.ArrayList<>();
    identityRows.add(List.of("Entity", bookIdentity.entityName().value()));
    identityRows.add(List.of("Functional currency", bookIdentity.functionalCurrency().code()));
    identityRows.add(List.of("Fiscal year start", bookIdentity.fiscalYearStart().wireValue()));
    identityRows.add(List.of("Posting coverage", displayPostingCoverage(postingCoverage)));
    identityRows.addAll(rows);
    return List.copyOf(identityRows);
  }

  private static String displayPostingCoverage(PostingCoverage postingCoverage) {
    return switch (postingCoverage) {
      case ALL_POSTING_KINDS -> "All posting kinds";
      case NON_CLOSING_POSTINGS -> "Non-closing postings";
    };
  }
}
