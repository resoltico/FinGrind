package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Renders semantic reporting payloads such as trial balances, ledgers, and period summaries. */
final class CliReportOutputRenderer {
  private static final int TEXT_TABLE_WIDTH = 120;

  private CliReportOutputRenderer() {}

  static String renderTrialBalanceText(TrialBalanceReport report) {
    boolean hasComparative = !report.comparativeRows().isEmpty();
    String header =
        CliTextFormat.renderKeyValueBlock(
            statementIdentityRows(
                report.bookIdentity(),
                report.postingCoverage(),
                hasComparative
                    ? report.comparativeEffectiveDateRange()
                    : EffectiveDateRange.unbounded(),
                List.of(
                    List.of(
                        "As of",
                        CliQueryOutputFormatter.upperDateBoundaryLabel(
                            report.effectiveDateAsOf().orElse(null))))));
    String totals =
        renderTrialBalanceTotals(
            report.totals(),
            report.balanced(),
            report.effectiveDateAsOf().orElse(null),
            "Current totals");
    String table =
        CliTextFormat.renderAdaptiveTable(
            TEXT_TABLE_WIDTH,
            List.of(
                "Account",
                "Name",
                "Currency",
                "Debit total",
                "Credit total",
                "Net amount",
                "Balance side"),
            report.rows().stream()
                .map(
                    row ->
                        List.of(
                            row.account().accountCode().value(),
                            row.account().accountName().value(),
                            row.balance().netAmount().currencyUnit().code(),
                            CliQueryOutputFormatter.displayMoney(row.balance().debitTotal()),
                            CliQueryOutputFormatter.displayMoney(row.balance().creditTotal()),
                            CliQueryOutputFormatter.displayMoney(row.balance().netAmount()),
                            CliQueryOutputFormatter.displayBalanceSideLabel(
                                row.balance().balanceSide())))
                .toList(),
            3,
            4,
            5);
    String comparative =
        !hasComparative
            ? ""
            : section(
                "Comparative Trial Balance",
                comparativeReferenceLine(report.comparativeEffectiveDateRange())
                    + System.lineSeparator()
                    + System.lineSeparator()
                    + renderTrialBalanceTotals(
                        report.comparativeTotals(),
                        report.comparativeBalanced(),
                        report.comparativeEffectiveDateRange().effectiveDateTo().orElse(null),
                        "Comparative totals")
                    + System.lineSeparator()
                    + System.lineSeparator()
                    + CliTextFormat.renderAdaptiveTable(
                        TEXT_TABLE_WIDTH,
                        List.of(
                            "Account",
                            "Name",
                            "Currency",
                            "Debit total",
                            "Credit total",
                            "Net amount",
                            "Balance side"),
                        report.comparativeRows().stream()
                            .map(
                                row ->
                                    List.of(
                                        row.account().accountCode().value(),
                                        row.account().accountName().value(),
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
                        3,
                        4,
                        5));
    return CliTextFormat.renderTitledBlock(
        "Trial Balance",
        joinSections(
            header
                + System.lineSeparator()
                + System.lineSeparator()
                + totals
                + System.lineSeparator()
                + System.lineSeparator()
                + table,
            comparative));
  }

  static String renderTrialBalanceCsv(TrialBalanceReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "reportBasis",
            "recordKind",
            "effectiveDateAsOf",
            "balanced",
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
        java.util.stream.Stream.of(
                report.rows().stream()
                    .map(
                        row ->
                            trialBalanceCsvRow(
                                "current",
                                report.effectiveDateAsOf().map(LocalDate::toString).orElse(""),
                                row)),
                report.totals().stream()
                    .map(
                        total ->
                            trialBalanceTotalCsvRow(
                                "current",
                                report.effectiveDateAsOf().map(LocalDate::toString).orElse(""),
                                report.balanced(),
                                total)),
                report.comparativeRows().stream()
                    .map(
                        row ->
                            trialBalanceCsvRow(
                                "comparative",
                                report
                                    .comparativeEffectiveDateRange()
                                    .effectiveDateTo()
                                    .map(LocalDate::toString)
                                    .orElse(""),
                                row)),
                report.comparativeTotals().stream()
                    .map(
                        total ->
                            trialBalanceTotalCsvRow(
                                "comparative",
                                report
                                    .comparativeEffectiveDateRange()
                                    .effectiveDateTo()
                                    .map(LocalDate::toString)
                                    .orElse(""),
                                report.comparativeBalanced(),
                                total)))
            .flatMap(stream -> stream)
            .toList());
  }

  static String renderAccountLedgerText(AccountLedgerReport report) {
    String header =
        CliTextFormat.renderKeyValueBlock(
            identityRows(
                report.bookIdentity(),
                report.postingCoverage(),
                List.of(
                    List.of("Account", report.account().accountCode().value()),
                    List.of("Name", report.account().accountName().value()),
                    List.of(
                        "Account type",
                        CliQueryOutputFormatter.displayLineTypeLabel(
                            report.account().accountType())),
                    List.of(
                        "Account role",
                        CliQueryOutputFormatter.displayAccountRoleLabel(
                            report.account().accountRole())),
                    List.of(
                        "Normal balance",
                        CliQueryOutputFormatter.displayNormalBalanceLabel(
                            report.account().normalBalance())),
                    List.of(
                        "Active",
                        CliQueryOutputFormatter.displayBooleanLabel(report.account().active())),
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
                        CliQueryOutputFormatter.joinedBalances(report.closingBalances())))));
    String entries =
        report.entries().isEmpty()
            ? "(none)"
            : CliTextFormat.renderTable(
                List.of(
                    "Effective date",
                    "Origin",
                    "Debit",
                    "Credit",
                    "Running",
                    "Counterparts",
                    "Posting"),
                report.entries().stream()
                    .map(
                        entry ->
                            CliQueryOutputFormatter.accountLedgerTextRow(report.account(), entry))
                    .toList(),
                2,
                3);
    return CliTextFormat.renderTitledBlock(
        "Account Ledger", joinSections(header, section("Entries", entries)));
  }

  static String renderAccountLedgerCsv(AccountLedgerReport report) {
    List<String> currencyCodes = accountLedgerCsvCurrencyCodes(report);
    return CliTextFormat.renderCsv(
        List.of(
            "rowKind",
            "accountCode",
            "accountName",
            "accountType",
            "accountRole",
            "normalBalance",
            "active",
            "effectiveDateFrom",
            "effectiveDateTo",
            "currencyCode",
            "openingDebitTotal",
            "openingCreditTotal",
            "openingNetAmount",
            "openingBalanceSide",
            "closingDebitTotal",
            "closingCreditTotal",
            "closingNetAmount",
            "closingBalanceSide",
            "effectiveDate",
            "recordedAt",
            "postingId",
            "postingKind",
            "postingOriginKind",
            "reversalState",
            "reversalTarget",
            "debitAmount",
            "creditAmount",
            "runningNetAmount",
            "runningBalanceSide",
            "counterpartAccounts",
            "sourceDocumentIds",
            "sourceDocumentTypes",
            "approvalIds",
            "approvalDecisions"),
        report.entries().isEmpty()
            ? currencyCodes.stream()
                .map(currencyCode -> accountLedgerSummaryCsvRow(report, currencyCode))
                .toList()
            : report.entries().stream()
                .map(entry -> accountLedgerEntryCsvRow(report, entry))
                .toList());
  }

  static String renderPeriodSummaryText(PeriodSummaryReport report) {
    String header =
        CliTextFormat.renderKeyValueBlock(
            identityRows(
                report.bookIdentity(),
                report.postingCoverage(),
                List.of(
                    List.of(
                        "Range", report.effectiveDateFrom() + " to " + report.effectiveDateTo()),
                    List.of("Posting count", Integer.toString(report.postingCount())),
                    List.of("Posting line count", Integer.toString(report.postingLineCount())),
                    List.of("Accounts touched", Integer.toString(report.accountsTouched())))));
    String currencyTotals =
        CliTextFormat.renderAdaptiveTable(
            TEXT_TABLE_WIDTH,
            List.of("Currency", "Debit total", "Credit total", "Net amount", "Balance side"),
            report.currencyTotals().stream()
                .map(summary -> CliQueryOutputFormatter.balanceTextRow(summary.totals()))
                .toList(),
            1,
            2,
            3);
    String accountActivity =
        CliTextFormat.renderAdaptiveTable(
            TEXT_TABLE_WIDTH,
            List.of(
                "Account",
                "Name",
                "Currency",
                "Debit total",
                "Credit total",
                "Net amount",
                "Balance side"),
            report.accountActivity().stream()
                .map(
                    row ->
                        List.of(
                            row.account().accountCode().value(),
                            row.account().accountName().value(),
                            row.movement().netAmount().currencyUnit().code(),
                            CliQueryOutputFormatter.displayMoney(row.movement().debitTotal()),
                            CliQueryOutputFormatter.displayMoney(row.movement().creditTotal()),
                            CliQueryOutputFormatter.displayMoney(row.movement().netAmount()),
                            CliQueryOutputFormatter.displayBalanceSideLabel(
                                row.movement().balanceSide())))
                .toList(),
            3,
            4,
            5);
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
            "recordKind",
            "subjectKind",
            "subjectCode",
            "subjectName",
            "metricName",
            "metricValue",
            "currencyCode",
            "metricUnit"),
        java.util.stream.Stream.concat(
                java.util.stream.Stream.of(
                    List.of(
                        "summary",
                        "book",
                        "",
                        "",
                        "postingCount",
                        Integer.toString(report.postingCount()),
                        "",
                        "count"),
                    List.of(
                        "summary",
                        "book",
                        "",
                        "",
                        "postingLineCount",
                        Integer.toString(report.postingLineCount()),
                        "",
                        "count"),
                    List.of(
                        "summary",
                        "book",
                        "",
                        "",
                        "accountsTouched",
                        Integer.toString(report.accountsTouched()),
                        "",
                        "count")),
                java.util.stream.Stream.concat(
                    report.currencyTotals().stream()
                        .flatMap(
                            summary ->
                                java.util.stream.Stream.of(
                                    List.of(
                                        "currency-total",
                                        "currency",
                                        summary.totals().netAmount().currencyUnit().code(),
                                        summary.totals().netAmount().currencyUnit().code(),
                                        "debitTotal",
                                        CliQueryOutputFormatter.displayMoney(
                                            summary.totals().debitTotal()),
                                        summary.totals().netAmount().currencyUnit().code(),
                                        "money"),
                                    List.of(
                                        "currency-total",
                                        "currency",
                                        summary.totals().netAmount().currencyUnit().code(),
                                        summary.totals().netAmount().currencyUnit().code(),
                                        "creditTotal",
                                        CliQueryOutputFormatter.displayMoney(
                                            summary.totals().creditTotal()),
                                        summary.totals().netAmount().currencyUnit().code(),
                                        "money"),
                                    List.of(
                                        "currency-total",
                                        "currency",
                                        summary.totals().netAmount().currencyUnit().code(),
                                        summary.totals().netAmount().currencyUnit().code(),
                                        "netAmount",
                                        CliQueryOutputFormatter.displayMoney(
                                            summary.totals().netAmount()),
                                        summary.totals().netAmount().currencyUnit().code(),
                                        "money"),
                                    List.of(
                                        "currency-total",
                                        "currency",
                                        summary.totals().netAmount().currencyUnit().code(),
                                        summary.totals().netAmount().currencyUnit().code(),
                                        "balanceSide",
                                        summary.totals().balanceSide().wireValue(),
                                        "",
                                        "enum"))),
                    report.accountActivity().stream()
                        .flatMap(
                            row ->
                                java.util.stream.Stream.of(
                                    List.of(
                                        "account-activity",
                                        "account",
                                        row.account().accountCode().value(),
                                        row.account().accountName().value(),
                                        "accountType",
                                        row.account().accountType().wireValue(),
                                        "",
                                        "enum"),
                                    List.of(
                                        "account-activity",
                                        "account",
                                        row.account().accountCode().value(),
                                        row.account().accountName().value(),
                                        "accountRole",
                                        row.account().accountRole().wireValue(),
                                        "",
                                        "enum"),
                                    List.of(
                                        "account-activity",
                                        "account",
                                        row.account().accountCode().value(),
                                        row.account().accountName().value(),
                                        "normalBalance",
                                        row.account().normalBalance().wireValue(),
                                        "",
                                        "enum"),
                                    List.of(
                                        "account-activity",
                                        "account",
                                        row.account().accountCode().value(),
                                        row.account().accountName().value(),
                                        "active",
                                        Boolean.toString(row.account().active()),
                                        "",
                                        "flag"),
                                    List.of(
                                        "account-activity",
                                        "account",
                                        row.account().accountCode().value(),
                                        row.account().accountName().value(),
                                        "declaredAt",
                                        row.account().declaredAt().toString(),
                                        "",
                                        "timestamp"),
                                    List.of(
                                        "account-activity",
                                        "account",
                                        row.account().accountCode().value(),
                                        row.account().accountName().value(),
                                        "debitTotal",
                                        CliQueryOutputFormatter.displayMoney(
                                            row.movement().debitTotal()),
                                        row.movement().netAmount().currencyUnit().code(),
                                        "money"),
                                    List.of(
                                        "account-activity",
                                        "account",
                                        row.account().accountCode().value(),
                                        row.account().accountName().value(),
                                        "creditTotal",
                                        CliQueryOutputFormatter.displayMoney(
                                            row.movement().creditTotal()),
                                        row.movement().netAmount().currencyUnit().code(),
                                        "money"),
                                    List.of(
                                        "account-activity",
                                        "account",
                                        row.account().accountCode().value(),
                                        row.account().accountName().value(),
                                        "netAmount",
                                        CliQueryOutputFormatter.displayMoney(
                                            row.movement().netAmount()),
                                        row.movement().netAmount().currencyUnit().code(),
                                        "money"),
                                    List.of(
                                        "account-activity",
                                        "account",
                                        row.account().accountCode().value(),
                                        row.account().accountName().value(),
                                        "balanceSide",
                                        row.movement().balanceSide().wireValue(),
                                        "",
                                        "enum")))))
            .toList());
  }

  private static List<String> csvRow(String... values) {
    return List.of(values);
  }

  private static List<String> accountLedgerSummaryCsvRow(
      AccountLedgerReport report, String currencyCode) {
    CurrencyBalance opening = balanceForCurrency(report.openingBalances(), currencyCode);
    CurrencyBalance closing = balanceForCurrency(report.closingBalances(), currencyCode);
    return csvRow(
        "summary",
        report.account().accountCode().value(),
        report.account().accountName().value(),
        report.account().accountType().wireValue(),
        report.account().accountRole().wireValue(),
        report.account().normalBalance().wireValue(),
        Boolean.toString(report.account().active()),
        report.effectiveDateRange().effectiveDateFrom().map(LocalDate::toString).orElse(""),
        report.effectiveDateRange().effectiveDateTo().map(LocalDate::toString).orElse(""),
        currencyCode,
        CliQueryOutputFormatter.displayMoney(opening.debitTotal()),
        CliQueryOutputFormatter.displayMoney(opening.creditTotal()),
        CliQueryOutputFormatter.displayMoney(opening.netAmount()),
        opening.balanceSide().wireValue(),
        CliQueryOutputFormatter.displayMoney(closing.debitTotal()),
        CliQueryOutputFormatter.displayMoney(closing.creditTotal()),
        CliQueryOutputFormatter.displayMoney(closing.netAmount()),
        closing.balanceSide().wireValue(),
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "");
  }

  private static List<String> accountLedgerEntryCsvRow(
      AccountLedgerReport report, dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry entry) {
    String currencyCode = entry.movement().netAmount().currencyUnit().code();
    CurrencyBalance opening = balanceForCurrency(report.openingBalances(), currencyCode);
    CurrencyBalance closing = balanceForCurrency(report.closingBalances(), currencyCode);
    return csvRow(
        "entry",
        report.account().accountCode().value(),
        report.account().accountName().value(),
        report.account().accountType().wireValue(),
        report.account().accountRole().wireValue(),
        report.account().normalBalance().wireValue(),
        Boolean.toString(report.account().active()),
        report.effectiveDateRange().effectiveDateFrom().map(LocalDate::toString).orElse(""),
        report.effectiveDateRange().effectiveDateTo().map(LocalDate::toString).orElse(""),
        currencyCode,
        CliQueryOutputFormatter.displayMoney(opening.debitTotal()),
        CliQueryOutputFormatter.displayMoney(opening.creditTotal()),
        CliQueryOutputFormatter.displayMoney(opening.netAmount()),
        opening.balanceSide().wireValue(),
        CliQueryOutputFormatter.displayMoney(closing.debitTotal()),
        CliQueryOutputFormatter.displayMoney(closing.creditTotal()),
        CliQueryOutputFormatter.displayMoney(closing.netAmount()),
        closing.balanceSide().wireValue(),
        entry.postingFact().journalEntry().effectiveDate().toString(),
        entry.postingFact().provenance().recordedAt().toString(),
        entry.postingFact().postingId().value(),
        entry.postingFact().postingKind().wireValue(),
        entry.postingFact().postingOriginKind().wireValue(),
        CliQueryOutputFormatter.reversalStateWireValue(entry.postingFact()),
        CliQueryOutputFormatter.reversalTargetCsv(entry.postingFact()),
        CliQueryOutputFormatter.displayMoney(entry.movement().debitTotal()),
        CliQueryOutputFormatter.displayMoney(entry.movement().creditTotal()),
        CliQueryOutputFormatter.displayMoney(entry.runningNetAmount()),
        entry.runningBalanceSide().wireValue(),
        CliQueryOutputFormatter.counterpartAccounts(report.account(), entry.postingFact()),
        CliQueryOutputFormatter.postingSourceDocumentIdsCsv(entry.postingFact()),
        CliQueryOutputFormatter.postingSourceDocumentTypesCsv(entry.postingFact()),
        CliQueryOutputFormatter.postingApprovalIdsCsv(entry.postingFact()),
        CliQueryOutputFormatter.postingApprovalDecisionsCsv(entry.postingFact()));
  }

  private static List<String> accountLedgerCsvCurrencyCodes(AccountLedgerReport report) {
    List<String> currencyCodes =
        java.util.stream.Stream.concat(
                java.util.stream.Stream.concat(
                    report.openingBalances().stream()
                        .map(balance -> balance.netAmount().currencyUnit().code()),
                    report.entries().stream()
                        .map(entry -> entry.movement().netAmount().currencyUnit().code())),
                report.closingBalances().stream()
                    .map(balance -> balance.netAmount().currencyUnit().code()))
            .distinct()
            .toList();
    return currencyCodes.isEmpty()
        ? List.of(report.bookIdentity().functionalCurrency().code())
        : currencyCodes;
  }

  static String renderFinancialPositionText(FinancialPositionReport report) {
    boolean hasComparative = CliReportSurfacePolicy.hasComparative(report);
    String header =
        CliTextFormat.renderKeyValueBlock(
            statementIdentityRows(
                report.bookIdentity(),
                report.postingCoverage(),
                hasComparative
                    ? report.comparativeEffectiveDateRange()
                    : EffectiveDateRange.unbounded(),
                List.of(
                    List.of(
                        "Effective date as of",
                        CliQueryOutputFormatter.upperDateBoundaryLabel(
                            report.effectiveDateAsOf().orElse(null))))));
    String sections = renderFinancialPositionSections(report.sections());
    String comparative =
        !hasComparative
            ? ""
            : section(
                "Comparative Financial Position",
                comparativeReferenceLine(report.comparativeEffectiveDateRange())
                    + System.lineSeparator()
                    + System.lineSeparator()
                    + renderFinancialPositionSections(report.comparativeSections()));
    return CliTextFormat.renderTitledBlock(
        "Financial Position",
        joinSections(
            header + System.lineSeparator() + System.lineSeparator() + sections, comparative));
  }

  static String renderFinancialPositionCsv(FinancialPositionReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "reportBasis",
            "recordKind",
            "effectiveDateAsOf",
            "sectionAccountType",
            "lineCode",
            "lineName",
            "lineRole",
            "lineType",
            "lineClassification",
            "lineKind",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "netAmount",
            "balanceSide"),
        java.util.stream.Stream.concat(
                financialPositionCsvRows(report, "current", report.sections()),
                financialPositionCsvRows(report, "comparative", report.comparativeSections()))
            .toList());
  }

  static String renderIncomeStatementText(IncomeStatementReport report) {
    boolean hasComparative = CliReportSurfacePolicy.hasComparative(report);
    String header =
        CliTextFormat.renderKeyValueBlock(
            statementIdentityRows(
                report.bookIdentity(),
                report.postingCoverage(),
                hasComparative
                    ? report.comparativeEffectiveDateRange()
                    : EffectiveDateRange.unbounded(),
                List.of(
                    List.of("Effective date from", report.effectiveDateFrom().toString()),
                    List.of("Effective date to", report.effectiveDateTo().toString()),
                    List.of(
                        "Net income totals",
                        report.netIncomeTotals().isEmpty()
                            ? "(none)"
                            : report.netIncomeTotals().stream()
                                .map(CliQueryOutputFormatter::displayBalanceText)
                                .collect(java.util.stream.Collectors.joining(", "))))));
    String sections = renderIncomeStatementSections(report.sections());
    String comparative =
        !hasComparative
            ? ""
            : section(
                "Comparative Income Statement",
                joinSections(
                    comparativeReferenceLine(report.comparativeEffectiveDateRange()),
                    renderIncomeStatementSections(report.comparativeSections()),
                    section(
                        "Comparative Net Income Totals",
                        joinedBalancesText(report.comparativeNetIncomeTotals()))));
    return CliTextFormat.renderTitledBlock(
        "Income Statement",
        joinSections(
            header + System.lineSeparator() + System.lineSeparator() + sections, comparative));
  }

  static String renderIncomeStatementCsv(IncomeStatementReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "reportBasis",
            "recordKind",
            "effectiveDateFrom",
            "effectiveDateTo",
            "sectionAccountType",
            "lineCode",
            "lineName",
            "lineRole",
            "lineType",
            "lineClassification",
            "lineKind",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "netAmount",
            "balanceSide"),
        java.util.stream.Stream.concat(
                incomeStatementCsvRows(
                    report, "current", report.sections(), report.netIncomeTotals()),
                incomeStatementCsvRows(
                    report,
                    "comparative",
                    report.comparativeSections(),
                    report.comparativeNetIncomeTotals()))
            .toList());
  }

  static String renderChangesInEquityText(ChangesInEquityReport report) {
    boolean hasComparative = CliReportSurfacePolicy.hasComparative(report);
    boolean hasCurrent = CliReportSurfacePolicy.hasCurrent(report);
    String header =
        CliTextFormat.renderKeyValueBlock(
            statementIdentityRows(
                report.bookIdentity(),
                report.postingCoverage(),
                hasComparative
                    ? report.comparativeEffectiveDateRange()
                    : EffectiveDateRange.unbounded(),
                changesInEquitySummaryRows(report, hasCurrent)));
    String table = renderChangesInEquityTable(report.rows());
    String comparative =
        !hasComparative
            ? ""
            : section(
                "Comparative Changes In Equity",
                joinSections(
                    comparativeReferenceLine(report.comparativeEffectiveDateRange()),
                    renderChangesInEquityTable(report.comparativeRows()),
                    comparativeChangesInEquityTotals(report)));
    return CliTextFormat.renderTitledBlock(
        "Changes In Equity",
        joinSections(
            table.isBlank()
                ? header
                : header + System.lineSeparator() + System.lineSeparator() + table,
            comparative));
  }

  static String renderChangesInEquityCsv(ChangesInEquityReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "reportBasis",
            "recordKind",
            "effectiveDateFrom",
            "effectiveDateTo",
            "lineCode",
            "lineName",
            "lineRole",
            "lineClassification",
            "lineKind",
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
        java.util.stream.Stream.concat(
                changesInEquityCsvRows(
                    report,
                    "current",
                    report.rows(),
                    report.openingTotals(),
                    report.movementTotals(),
                    report.closingTotals()),
                changesInEquityCsvRows(
                    report,
                    "comparative",
                    report.comparativeRows(),
                    report.comparativeOpeningTotals(),
                    report.comparativeMovementTotals(),
                    report.comparativeClosingTotals()))
            .toList());
  }

  private static String joinedBalancesText(List<dev.erst.fingrind.core.CurrencyBalance> balances) {
    if (balances.isEmpty()) {
      return "(none)";
    }
    return balances.stream()
        .map(CliQueryOutputFormatter::displayBalanceText)
        .collect(java.util.stream.Collectors.joining(", "));
  }

  private static String renderStatementSection(
      String title,
      String table,
      List<dev.erst.fingrind.core.CurrencyBalance> totals,
      String totalsLabel) {
    java.util.List<String> bodySections = new java.util.ArrayList<>();
    if (!table.isBlank()) {
      bodySections.add(table);
    }
    if (!totals.isEmpty()) {
      bodySections.add(
          CliTextFormat.renderKeyValueBlock(
              List.of(List.of(totalsLabel, joinedBalancesText(totals)))));
    }
    return title
        + System.lineSeparator()
        + "-".repeat(title.length())
        + System.lineSeparator()
        + joinSections(bodySections.toArray(String[]::new));
  }

  private static String joinSections(String... sections) {
    return java.util.Arrays.stream(sections)
        .filter(section -> !section.isBlank())
        .collect(
            java.util.stream.Collectors.joining(System.lineSeparator() + System.lineSeparator()));
  }

  private static String section(String title, String body) {
    return title
        + System.lineSeparator()
        + "-".repeat(title.length())
        + System.lineSeparator()
        + body;
  }

  private static List<List<String>> statementIdentityRows(
      BookIdentity bookIdentity,
      PostingCoverage postingCoverage,
      EffectiveDateRange comparativeEffectiveDateRange,
      List<List<String>> rows) {
    List<List<String>> identityRows =
        new java.util.ArrayList<>(identityRows(bookIdentity, postingCoverage, List.of()));
    if (comparativeEffectiveDateRange.effectiveDateFrom().isPresent()
        || comparativeEffectiveDateRange.effectiveDateTo().isPresent()) {
      identityRows.add(
          List.of(
              "Comparative reference", comparativeReferenceLine(comparativeEffectiveDateRange)));
    }
    identityRows.addAll(rows);
    return List.copyOf(identityRows);
  }

  private static List<List<String>> identityRows(
      BookIdentity bookIdentity, PostingCoverage postingCoverage, List<List<String>> rows) {
    List<List<String>> identityRows =
        new java.util.ArrayList<>(CliBookIdentityDisplay.summaryRows(bookIdentity));
    identityRows.add(List.of("Posting coverage", displayPostingCoverage(postingCoverage)));
    identityRows.addAll(rows);
    return List.copyOf(identityRows);
  }

  private static String comparativeReferenceLine(EffectiveDateRange comparativeEffectiveDateRange) {
    if (comparativeEffectiveDateRange.effectiveDateFrom().isEmpty()
        && comparativeEffectiveDateRange.effectiveDateTo().isEmpty()) {
      return "(none)";
    }
    return CliQueryOutputFormatter.dateRange(
        comparativeEffectiveDateRange.effectiveDateFrom().orElse(null),
        comparativeEffectiveDateRange.effectiveDateTo().orElse(null));
  }

  private static String renderFinancialPositionSections(
      List<dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection> sections) {
    List<dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection> nonEmptySections =
        sections.stream()
            .filter(CliReportOutputRenderer::hasRenderableFinancialPositionSection)
            .toList();
    return nonEmptySections.isEmpty()
        ? "(none)"
        : nonEmptySections.stream()
            .map(
                section ->
                    renderStatementSection(
                        CliQueryOutputFormatter.displayAccountTypeSectionLabel(
                            section.accountType()),
                        section.rows().isEmpty()
                            ? ""
                            : CliTextFormat.renderAdaptiveTable(
                                TEXT_TABLE_WIDTH,
                                List.of(
                                    "Line code",
                                    "Line name",
                                    "Classification",
                                    "Net amount",
                                    "Balance side"),
                                section.rows().stream()
                                    .map(
                                        row ->
                                            List.of(
                                                CliQueryOutputFormatter.displayStatementLineCode(
                                                    row.lineCode(), row.lineKind()),
                                                row.lineName(),
                                                CliQueryOutputFormatter
                                                    .displayFinancialPositionLineClassification(
                                                        row.lineClassification()),
                                                CliQueryOutputFormatter.displayMoney(
                                                    row.balance().netAmount()),
                                                CliQueryOutputFormatter.displayBalanceSideLabel(
                                                    row.balance().balanceSide())))
                                    .toList(),
                                3),
                        section.totals(),
                        "Section totals"))
            .collect(
                java.util.stream.Collectors.joining(
                    System.lineSeparator() + System.lineSeparator()));
  }

  private static java.util.stream.Stream<List<String>> financialPositionCsvRows(
      FinancialPositionReport report,
      String reportBasis,
      List<dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection> sections) {
    String effectiveDateAsOf =
        "comparative".equals(reportBasis)
            ? report
                .comparativeEffectiveDateRange()
                .effectiveDateTo()
                .map(LocalDate::toString)
                .orElse("")
            : report.effectiveDateAsOf().map(LocalDate::toString).orElse("");
    return sections.stream()
        .flatMap(
            section ->
                java.util.stream.Stream.concat(
                    section.rows().stream()
                        .map(
                            row ->
                                List.of(
                                    reportBasis,
                                    "row",
                                    effectiveDateAsOf,
                                    section.accountType().wireValue(),
                                    row.lineCode(),
                                    row.lineName(),
                                    row.lineRole()
                                        .map(dev.erst.fingrind.core.AccountRole::wireValue)
                                        .orElse(""),
                                    row.lineType().wireValue(),
                                    row.lineClassification()
                                        .map(
                                            dev.erst.fingrind.core
                                                    .FinancialPositionLineClassification
                                                ::wireValue)
                                        .orElse(""),
                                    row.lineKind().wireValue(),
                                    row.balance().netAmount().currencyUnit().code(),
                                    CliQueryOutputFormatter.displayMoney(
                                        row.balance().debitTotal()),
                                    CliQueryOutputFormatter.displayMoney(
                                        row.balance().creditTotal()),
                                    CliQueryOutputFormatter.displayMoney(row.balance().netAmount()),
                                    row.balance().balanceSide().wireValue())),
                    section.totals().stream()
                        .map(
                            total ->
                                List.of(
                                    reportBasis,
                                    "section-total",
                                    effectiveDateAsOf,
                                    section.accountType().wireValue(),
                                    section
                                            .accountType()
                                            .wireValue()
                                            .toLowerCase(java.util.Locale.ROOT)
                                        + "-total",
                                    CliQueryOutputFormatter.displayAccountTypeSectionLabel(
                                            section.accountType())
                                        + " total",
                                    "",
                                    section.accountType().wireValue(),
                                    "",
                                    "SECTION_TOTAL",
                                    total.netAmount().currencyUnit().code(),
                                    CliQueryOutputFormatter.displayMoney(total.debitTotal()),
                                    CliQueryOutputFormatter.displayMoney(total.creditTotal()),
                                    CliQueryOutputFormatter.displayMoney(total.netAmount()),
                                    total.balanceSide().wireValue()))));
  }

  private static String renderIncomeStatementSections(
      List<dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection> sections) {
    List<dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection> nonEmptySections =
        sections.stream()
            .filter(CliReportOutputRenderer::hasRenderableIncomeStatementSection)
            .toList();
    return nonEmptySections.isEmpty()
        ? "(none)"
        : nonEmptySections.stream()
            .map(
                section ->
                    renderStatementSection(
                        CliQueryOutputFormatter.displayAccountTypeSectionLabel(
                            section.accountType()),
                        section.rows().isEmpty()
                            ? ""
                            : CliTextFormat.renderAdaptiveTable(
                                TEXT_TABLE_WIDTH,
                                List.of(
                                    "Line code",
                                    "Line name",
                                    "Classification",
                                    "Net amount",
                                    "Balance side"),
                                section.rows().stream()
                                    .map(
                                        row ->
                                            List.of(
                                                CliQueryOutputFormatter.displayStatementLineCode(
                                                    row.lineCode(), row.lineKind()),
                                                row.lineName(),
                                                CliQueryOutputFormatter
                                                    .displayProfitAndLossLineClassification(
                                                        row.lineClassification()),
                                                CliQueryOutputFormatter.displayMoney(
                                                    row.movement().netAmount()),
                                                CliQueryOutputFormatter.displayBalanceSideLabel(
                                                    row.movement().balanceSide())))
                                    .toList(),
                                3),
                        section.totals(),
                        "Section totals"))
            .collect(
                java.util.stream.Collectors.joining(
                    System.lineSeparator() + System.lineSeparator()));
  }

  private static java.util.stream.Stream<List<String>> incomeStatementCsvRows(
      IncomeStatementReport report,
      String reportBasis,
      List<dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection> sections,
      List<dev.erst.fingrind.core.CurrencyBalance> netIncomeTotals) {
    String effectiveDateFrom =
        "comparative".equals(reportBasis)
            ? report
                .comparativeEffectiveDateRange()
                .effectiveDateFrom()
                .map(LocalDate::toString)
                .orElse("")
            : report.effectiveDateFrom().toString();
    String effectiveDateTo =
        "comparative".equals(reportBasis)
            ? report
                .comparativeEffectiveDateRange()
                .effectiveDateTo()
                .map(LocalDate::toString)
                .orElse("")
            : report.effectiveDateTo().toString();
    java.util.stream.Stream<List<String>> sectionRows =
        sections.stream()
            .flatMap(
                section ->
                    java.util.stream.Stream.concat(
                        section.rows().stream()
                            .map(
                                row ->
                                    List.of(
                                        reportBasis,
                                        "row",
                                        effectiveDateFrom,
                                        effectiveDateTo,
                                        section.accountType().wireValue(),
                                        row.lineCode(),
                                        row.lineName(),
                                        row.lineRole()
                                            .map(dev.erst.fingrind.core.AccountRole::wireValue)
                                            .orElse(""),
                                        row.lineType().wireValue(),
                                        row.lineClassification().wireValue(),
                                        row.lineKind().wireValue(),
                                        row.movement().netAmount().currencyUnit().code(),
                                        CliQueryOutputFormatter.displayMoney(
                                            row.movement().debitTotal()),
                                        CliQueryOutputFormatter.displayMoney(
                                            row.movement().creditTotal()),
                                        CliQueryOutputFormatter.displayMoney(
                                            row.movement().netAmount()),
                                        row.movement().balanceSide().wireValue())),
                        section.totals().stream()
                            .map(
                                total ->
                                    List.of(
                                        reportBasis,
                                        "section-total",
                                        effectiveDateFrom,
                                        effectiveDateTo,
                                        section.accountType().wireValue(),
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        total.netAmount().currencyUnit().code(),
                                        CliQueryOutputFormatter.displayMoney(total.debitTotal()),
                                        CliQueryOutputFormatter.displayMoney(total.creditTotal()),
                                        CliQueryOutputFormatter.displayMoney(total.netAmount()),
                                        total.balanceSide().wireValue()))));
    java.util.stream.Stream<List<String>> totalRows =
        netIncomeTotals.stream()
            .map(
                total ->
                    List.of(
                        reportBasis,
                        "net-income-total",
                        effectiveDateFrom,
                        effectiveDateTo,
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        total.netAmount().currencyUnit().code(),
                        CliQueryOutputFormatter.displayMoney(total.debitTotal()),
                        CliQueryOutputFormatter.displayMoney(total.creditTotal()),
                        CliQueryOutputFormatter.displayMoney(total.netAmount()),
                        total.balanceSide().wireValue()));
    return java.util.stream.Stream.concat(sectionRows, totalRows);
  }

  private static java.util.stream.Stream<List<String>> changesInEquityCsvRows(
      ChangesInEquityReport report,
      String reportBasis,
      List<dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow> rows,
      List<dev.erst.fingrind.core.CurrencyBalance> openingTotals,
      List<dev.erst.fingrind.core.CurrencyBalance> movementTotals,
      List<dev.erst.fingrind.core.CurrencyBalance> closingTotals) {
    String effectiveDateFrom =
        "comparative".equals(reportBasis)
            ? report
                .comparativeEffectiveDateRange()
                .effectiveDateFrom()
                .map(LocalDate::toString)
                .orElse("")
            : report.effectiveDateFrom().toString();
    String effectiveDateTo =
        "comparative".equals(reportBasis)
            ? report
                .comparativeEffectiveDateRange()
                .effectiveDateTo()
                .map(LocalDate::toString)
                .orElse("")
            : report.effectiveDateTo().toString();
    java.util.stream.Stream<List<String>> rowStream =
        rows.stream()
            .map(
                row ->
                    List.of(
                        reportBasis,
                        "row",
                        effectiveDateFrom,
                        effectiveDateTo,
                        row.lineCode(),
                        row.lineName(),
                        row.lineRole()
                            .map(dev.erst.fingrind.core.AccountRole::wireValue)
                            .orElse(""),
                        row.lineClassification()
                            .map(
                                dev.erst.fingrind.core.FinancialPositionLineClassification
                                    ::wireValue)
                            .orElse(""),
                        row.lineKind().wireValue(),
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
                        row.closingBalance().balanceSide().wireValue()));
    java.util.stream.Stream<List<String>> totalStream =
        equityReportTotalCsvRows(report, reportBasis, openingTotals, movementTotals, closingTotals);
    return java.util.stream.Stream.concat(rowStream, totalStream);
  }

  private static java.util.stream.Stream<List<String>> equityReportTotalCsvRows(
      ChangesInEquityReport report,
      String reportBasis,
      List<dev.erst.fingrind.core.CurrencyBalance> openingTotals,
      List<dev.erst.fingrind.core.CurrencyBalance> movementTotals,
      List<dev.erst.fingrind.core.CurrencyBalance> closingTotals) {
    String effectiveDateFrom =
        "comparative".equals(reportBasis)
            ? report
                .comparativeEffectiveDateRange()
                .effectiveDateFrom()
                .map(LocalDate::toString)
                .orElse("")
            : report.effectiveDateFrom().toString();
    String effectiveDateTo =
        "comparative".equals(reportBasis)
            ? report
                .comparativeEffectiveDateRange()
                .effectiveDateTo()
                .map(LocalDate::toString)
                .orElse("")
            : report.effectiveDateTo().toString();
    List<String> currencyCodes =
        java.util.stream.Stream.of(openingTotals, movementTotals, closingTotals)
            .flatMap(List::stream)
            .map(total -> total.netAmount().currencyUnit().code())
            .distinct()
            .toList();
    return currencyCodes.stream()
        .map(
            currencyCode -> {
              CurrencyBalance opening = balanceForCurrency(openingTotals, currencyCode);
              CurrencyBalance movement = balanceForCurrency(movementTotals, currencyCode);
              CurrencyBalance closing = balanceForCurrency(closingTotals, currencyCode);
              return List.of(
                  reportBasis,
                  "report-total",
                  effectiveDateFrom,
                  effectiveDateTo,
                  "report-total",
                  "Report total",
                  "",
                  "",
                  "REPORT_TOTAL",
                  currencyCode,
                  CliQueryOutputFormatter.displayMoney(opening.debitTotal()),
                  CliQueryOutputFormatter.displayMoney(opening.creditTotal()),
                  CliQueryOutputFormatter.displayMoney(opening.netAmount()),
                  opening.balanceSide().wireValue(),
                  CliQueryOutputFormatter.displayMoney(movement.debitTotal()),
                  CliQueryOutputFormatter.displayMoney(movement.creditTotal()),
                  CliQueryOutputFormatter.displayMoney(movement.netAmount()),
                  movement.balanceSide().wireValue(),
                  CliQueryOutputFormatter.displayMoney(closing.debitTotal()),
                  CliQueryOutputFormatter.displayMoney(closing.creditTotal()),
                  CliQueryOutputFormatter.displayMoney(closing.netAmount()),
                  closing.balanceSide().wireValue());
            });
  }

  private static String renderTrialBalanceTotals(
      List<CurrencyBalance> totals,
      boolean balanced,
      @Nullable LocalDate effectiveDateAsOf,
      String title) {
    String summary =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("As of", CliQueryOutputFormatter.upperDateBoundaryLabel(effectiveDateAsOf)),
                List.of(
                    "Balance state", CliQueryOutputFormatter.displayBalanceStateLabel(balanced))));
    String table =
        CliTextFormat.renderTable(
            List.of("Currency", "Debit total", "Credit total", "Net amount", "Balance side"),
            totals.stream()
                .map(
                    total ->
                        List.of(
                            total.netAmount().currencyUnit().code(),
                            CliQueryOutputFormatter.displayMoney(total.debitTotal()),
                            CliQueryOutputFormatter.displayMoney(total.creditTotal()),
                            CliQueryOutputFormatter.displayMoney(total.netAmount()),
                            CliQueryOutputFormatter.displayBalanceSideLabel(total.balanceSide())))
                .toList(),
            1,
            2,
            3);
    return section(title, joinSections(summary, table));
  }

  private static List<String> trialBalanceCsvRow(
      String reportBasis,
      String effectiveDateAsOf,
      dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow row) {
    return List.of(
        reportBasis,
        "row",
        effectiveDateAsOf,
        "",
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
        row.balance().balanceSide().wireValue());
  }

  private static List<String> trialBalanceTotalCsvRow(
      String reportBasis, String effectiveDateAsOf, boolean balanced, CurrencyBalance total) {
    return List.of(
        reportBasis,
        "total",
        effectiveDateAsOf,
        Boolean.toString(balanced),
        "",
        "",
        "",
        "",
        "",
        total.netAmount().currencyUnit().code(),
        CliQueryOutputFormatter.displayMoney(total.debitTotal()),
        CliQueryOutputFormatter.displayMoney(total.creditTotal()),
        CliQueryOutputFormatter.displayMoney(total.netAmount()),
        total.balanceSide().wireValue());
  }

  private static String displayPostingCoverage(PostingCoverage postingCoverage) {
    return CliQueryOutputFormatter.displayPostingCoverage(postingCoverage);
  }

  private static boolean hasRenderableFinancialPositionSection(
      dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection section) {
    return CliReportSurfacePolicy.hasRenderableFinancialPositionSection(section);
  }

  private static boolean hasRenderableIncomeStatementSection(
      dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection section) {
    return CliReportSurfacePolicy.hasRenderableIncomeStatementSection(section);
  }

  private static List<List<String>> changesInEquitySummaryRows(
      ChangesInEquityReport report, boolean hasCurrent) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Effective date from", report.effectiveDateFrom().toString()));
    rows.add(List.of("Effective date to", report.effectiveDateTo().toString()));
    if (!report.openingTotals().isEmpty()) {
      rows.add(List.of("Opening totals", joinedBalancesText(report.openingTotals())));
    }
    if (!report.movementTotals().isEmpty()) {
      rows.add(List.of("Movement totals", joinedBalancesText(report.movementTotals())));
    }
    if (!report.closingTotals().isEmpty()) {
      rows.add(List.of("Closing totals", joinedBalancesText(report.closingTotals())));
    }
    if (!hasCurrent) {
      rows.add(List.of("Outcome", "No equity balances or movements matched the selected period."));
    }
    return List.copyOf(rows);
  }

  private static String renderChangesInEquityTable(
      List<dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow> rows) {
    if (rows.isEmpty()) {
      return "";
    }
    return CliTextFormat.renderAdaptiveTable(
        TEXT_TABLE_WIDTH,
        List.of("Line code", "Line name", "Opening", "Movement", "Closing", "Closing side"),
        rows.stream()
            .map(
                row ->
                    List.of(
                        CliQueryOutputFormatter.displayStatementLineCode(
                            row.lineCode(), row.lineKind()),
                        row.lineName(),
                        CliQueryOutputFormatter.displayMoney(row.openingBalance().netAmount()),
                        CliQueryOutputFormatter.displayMoney(row.movement().netAmount()),
                        CliQueryOutputFormatter.displayMoney(row.closingBalance().netAmount()),
                        CliQueryOutputFormatter.displayBalanceSideLabel(
                            row.closingBalance().balanceSide())))
            .toList(),
        2,
        3);
  }

  private static CurrencyBalance balanceForCurrency(
      List<CurrencyBalance> balances, String currencyCode) {
    return balances.stream()
        .filter(balance -> balance.netAmount().currencyUnit().code().equals(currencyCode))
        .findFirst()
        .orElseGet(
            () ->
                CurrencyBalance.ofTotals(
                    Money.zero(CurrencyUnit.of(currencyCode)),
                    Money.zero(CurrencyUnit.of(currencyCode))));
  }

  private static String comparativeChangesInEquityTotals(ChangesInEquityReport report) {
    List<List<String>> rows = new java.util.ArrayList<>();
    if (!report.comparativeOpeningTotals().isEmpty()) {
      rows.add(
          List.of(
              "Comparative opening totals", joinedBalancesText(report.comparativeOpeningTotals())));
    }
    if (!report.comparativeMovementTotals().isEmpty()) {
      rows.add(
          List.of(
              "Comparative movement totals",
              joinedBalancesText(report.comparativeMovementTotals())));
    }
    if (!report.comparativeClosingTotals().isEmpty()) {
      rows.add(
          List.of(
              "Comparative closing totals", joinedBalancesText(report.comparativeClosingTotals())));
    }
    return rows.isEmpty() ? "" : CliTextFormat.renderKeyValueBlock(List.copyOf(rows));
  }
}
