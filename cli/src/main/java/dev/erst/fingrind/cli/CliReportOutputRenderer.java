package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Renders semantic reporting payloads such as trial balances, ledgers, and period summaries. */
final class CliReportOutputRenderer {
  private static final int HUMAN_TABLE_WIDTH = 120;

  private CliReportOutputRenderer() {}

  static String renderTrialBalanceHuman(TrialBalanceReport report) {
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
            HUMAN_TABLE_WIDTH,
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
                        HUMAN_TABLE_WIDTH,
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
                        report.comparativeRows().stream()
                            .map(CliQueryOutputFormatter::trialBalanceHumanRow)
                            .toList(),
                        6,
                        7,
                        8));
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

  static String renderAccountLedgerHuman(AccountLedgerReport report) {
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
    String entries = renderAccountLedgerEntries(report);
    return CliTextFormat.renderTitledBlock(
        "Account Ledger", joinSections(header, section("Entries", entries)));
  }

  static String renderAccountLedgerCsv(AccountLedgerReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "recordKind",
            "currencyCode",
            "bucketDebitTotal",
            "bucketCreditTotal",
            "bucketNetAmount",
            "bucketBalanceSide",
            "postingId",
            "postingKind",
            "reversalState",
            "reversalTarget",
            "effectiveDate",
            "recordedAt",
            "debitAmount",
            "creditAmount",
            "runningNetAmount",
            "runningBalanceSide",
            "counterpartAccounts",
            "sourceDocuments",
            "approvals"),
        java.util.stream.Stream.concat(
                java.util.stream.Stream.concat(
                    report.openingBalances().stream()
                        .map(
                            balance ->
                                List.of(
                                    "opening-balance",
                                    balance.netAmount().currencyUnit().code(),
                                    CliQueryOutputFormatter.displayMoney(balance.debitTotal()),
                                    CliQueryOutputFormatter.displayMoney(balance.creditTotal()),
                                    CliQueryOutputFormatter.displayMoney(balance.netAmount()),
                                    balance.balanceSide().wireValue(),
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
                                    "")),
                    report.entries().stream()
                        .map(
                            entry ->
                                List.of(
                                    "ledger-entry",
                                    entry.movement().netAmount().currencyUnit().code(),
                                    "",
                                    "",
                                    "",
                                    "",
                                    entry.postingFact().postingId().value(),
                                    entry.postingFact().postingKind().wireValue(),
                                    CliQueryOutputFormatter.reversalStateWireValue(
                                        entry.postingFact()),
                                    CliQueryOutputFormatter.reversalTargetCsv(entry.postingFact()),
                                    entry.postingFact().journalEntry().effectiveDate().toString(),
                                    entry.postingFact().provenance().recordedAt().toString(),
                                    CliQueryOutputFormatter.displayMoney(
                                        entry.movement().debitTotal()),
                                    CliQueryOutputFormatter.displayMoney(
                                        entry.movement().creditTotal()),
                                    CliQueryOutputFormatter.displayMoney(entry.runningNetAmount()),
                                    entry.runningBalanceSide().wireValue(),
                                    CliQueryOutputFormatter.counterpartAccounts(
                                        report.account(), entry.postingFact()),
                                    CliQueryOutputFormatter.postingSourceDocumentsCsv(
                                        entry.postingFact()),
                                    CliQueryOutputFormatter.postingApprovalsCsv(
                                        entry.postingFact())))),
                report.closingBalances().stream()
                    .map(
                        balance ->
                            List.of(
                                "closing-balance",
                                balance.netAmount().currencyUnit().code(),
                                CliQueryOutputFormatter.displayMoney(balance.debitTotal()),
                                CliQueryOutputFormatter.displayMoney(balance.creditTotal()),
                                CliQueryOutputFormatter.displayMoney(balance.netAmount()),
                                balance.balanceSide().wireValue(),
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
                                "")))
            .toList());
  }

  static String renderPeriodSummaryHuman(PeriodSummaryReport report) {
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
            HUMAN_TABLE_WIDTH,
            List.of("Currency", "Debit total", "Credit total", "Net amount", "Balance side"),
            report.currencyTotals().stream()
                .map(summary -> CliQueryOutputFormatter.balanceHumanRow(summary.totals()))
                .toList(),
            1,
            2,
            3);
    String accountActivity =
        CliTextFormat.renderAdaptiveTable(
            HUMAN_TABLE_WIDTH,
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
            "recordKind",
            "postingCount",
            "postingLineCount",
            "accountsTouched",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "netAmount",
            "balanceSide",
            "accountCode",
            "accountName",
            "accountType",
            "accountRole",
            "normalBalance",
            "active",
            "declaredAt"),
        java.util.stream.Stream.concat(
                java.util.stream.Stream.of(
                    List.of(
                        "summary",
                        Integer.toString(report.postingCount()),
                        Integer.toString(report.postingLineCount()),
                        Integer.toString(report.accountsTouched()),
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
                        "")),
                java.util.stream.Stream.concat(
                    report.currencyTotals().stream()
                        .map(
                            summary ->
                                List.of(
                                    "currency-total",
                                    "",
                                    "",
                                    "",
                                    summary.totals().netAmount().currencyUnit().code(),
                                    CliQueryOutputFormatter.displayMoney(
                                        summary.totals().debitTotal()),
                                    CliQueryOutputFormatter.displayMoney(
                                        summary.totals().creditTotal()),
                                    CliQueryOutputFormatter.displayMoney(
                                        summary.totals().netAmount()),
                                    summary.totals().balanceSide().wireValue(),
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    "")),
                    report.accountActivity().stream()
                        .map(
                            row ->
                                List.of(
                                    "account-activity",
                                    "",
                                    "",
                                    "",
                                    row.movement().netAmount().currencyUnit().code(),
                                    CliQueryOutputFormatter.displayMoney(
                                        row.movement().debitTotal()),
                                    CliQueryOutputFormatter.displayMoney(
                                        row.movement().creditTotal()),
                                    CliQueryOutputFormatter.displayMoney(
                                        row.movement().netAmount()),
                                    row.movement().balanceSide().wireValue(),
                                    row.account().accountCode().value(),
                                    row.account().accountName().value(),
                                    row.account().accountType().wireValue(),
                                    row.account().accountRole().wireValue(),
                                    row.account().normalBalance().wireValue(),
                                    Boolean.toString(row.account().active()),
                                    row.account().declaredAt().toString()))))
            .toList());
  }

  static String renderFinancialPositionHuman(FinancialPositionReport report) {
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

  static String renderIncomeStatementHuman(IncomeStatementReport report) {
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
                                .map(CliQueryOutputFormatter::displayBalanceHuman)
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
                        joinedBalancesHuman(report.comparativeNetIncomeTotals()))));
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

  static String renderChangesInEquityHuman(ChangesInEquityReport report) {
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
            "totalBasis",
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

  private static String joinedBalancesHuman(List<dev.erst.fingrind.core.CurrencyBalance> balances) {
    if (balances.isEmpty()) {
      return "(none)";
    }
    return balances.stream()
        .map(CliQueryOutputFormatter::displayBalanceHuman)
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
              List.of(List.of(totalsLabel, joinedBalancesHuman(totals)))));
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

  private static String renderAccountLedgerEntries(AccountLedgerReport report) {
    if (report.entries().isEmpty()) {
      return "(none)";
    }
    List<String> entryBlocks =
        report.entries().stream()
            .map(entry -> renderAccountLedgerEntry(report.account(), entry))
            .toList();
    return String.join(System.lineSeparator() + System.lineSeparator(), entryBlocks);
  }

  private static String renderAccountLedgerEntry(
      dev.erst.fingrind.contract.bookkeeping.DeclaredAccount account,
      dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry entry) {
    String summary = CliPostingOutputRenderer.postingHeadline(entry.postingFact());
    String details =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Recorded at",
                    CliHumanDisplay.instant(entry.postingFact().provenance().recordedAt())),
                List.of(
                    "Posting role",
                    CliQueryOutputFormatter.displayPostingRoleHuman(entry.postingFact())),
                List.of(
                    "Reverses posting",
                    CliQueryOutputFormatter.reversalTargetHuman(entry.postingFact())),
                List.of("Currency", entry.movement().netAmount().currencyUnit().code()),
                List.of(
                    "Debit", CliQueryOutputFormatter.displayMoney(entry.movement().debitTotal())),
                List.of(
                    "Credit", CliQueryOutputFormatter.displayMoney(entry.movement().creditTotal())),
                List.of(
                    "Running balance",
                    CliQueryOutputFormatter.displayMoney(entry.runningNetAmount())),
                List.of(
                    "Balance side",
                    CliQueryOutputFormatter.displayBalanceSideLabel(entry.runningBalanceSide())),
                List.of(
                    "Counterpart accounts",
                    CliQueryOutputFormatter.counterpartAccounts(account, entry.postingFact())),
                List.of(
                    "Source documents",
                    CliQueryOutputFormatter.postingSourceDocumentsHuman(entry.postingFact())),
                List.of(
                    "Approvals",
                    CliQueryOutputFormatter.postingApprovalsHuman(entry.postingFact()))));
    return summary
        + System.lineSeparator()
        + "-".repeat(summary.length())
        + System.lineSeparator()
        + details;
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
                                HUMAN_TABLE_WIDTH,
                                List.of(
                                    "Line code",
                                    "Line name",
                                    "Role",
                                    "Classification",
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
                                                CliQueryOutputFormatter.displayStatementLineCode(
                                                    row.lineCode(), row.lineKind()),
                                                row.lineName(),
                                                CliQueryOutputFormatter.displayLineRole(
                                                    row.lineRole()),
                                                CliQueryOutputFormatter
                                                    .displayFinancialPositionLineClassification(
                                                        row.lineClassification()),
                                                CliQueryOutputFormatter.displayRowKind(
                                                    row.lineKind()),
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
                                5,
                                6,
                                7),
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
                                    row.lineClassification().wireValue(),
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
                                HUMAN_TABLE_WIDTH,
                                List.of(
                                    "Line code",
                                    "Line name",
                                    "Role",
                                    "Classification",
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
                                                CliQueryOutputFormatter.displayStatementLineCode(
                                                    row.lineCode(), row.lineKind()),
                                                row.lineName(),
                                                CliQueryOutputFormatter.displayLineRole(
                                                    row.lineRole()),
                                                CliQueryOutputFormatter
                                                    .displayProfitAndLossLineClassification(
                                                        row.lineClassification()),
                                                CliQueryOutputFormatter.displayRowKind(
                                                    row.lineKind()),
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
                                5,
                                6,
                                7),
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
                        "",
                        row.lineCode(),
                        row.lineName(),
                        row.lineRole()
                            .map(dev.erst.fingrind.core.AccountRole::wireValue)
                            .orElse(""),
                        row.lineClassification().wireValue(),
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
        java.util.stream.Stream.of(
                equityTotalCsvRows(report, reportBasis, "opening", openingTotals),
                equityTotalCsvRows(report, reportBasis, "movement", movementTotals),
                equityTotalCsvRows(report, reportBasis, "closing", closingTotals))
            .flatMap(stream -> stream);
    return java.util.stream.Stream.concat(rowStream, totalStream);
  }

  private static java.util.stream.Stream<List<String>> equityTotalCsvRows(
      ChangesInEquityReport report,
      String reportBasis,
      String totalBasis,
      List<dev.erst.fingrind.core.CurrencyBalance> totals) {
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
    return totals.stream()
        .map(
            total ->
                List.of(
                    reportBasis,
                    "report-total",
                    effectiveDateFrom,
                    effectiveDateTo,
                    totalBasis,
                    "",
                    "",
                    "",
                    "",
                    total.netAmount().currencyUnit().code(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    CliQueryOutputFormatter.displayMoney(total.debitTotal()),
                    CliQueryOutputFormatter.displayMoney(total.creditTotal()),
                    CliQueryOutputFormatter.displayMoney(total.netAmount()),
                    total.balanceSide().wireValue()));
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
                List.of("Balanced", CliQueryOutputFormatter.displayBooleanLabel(balanced))));
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
                            total.balanceSide().wireValue()))
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
      rows.add(List.of("Opening totals", joinedBalancesHuman(report.openingTotals())));
    }
    if (!report.movementTotals().isEmpty()) {
      rows.add(List.of("Movement totals", joinedBalancesHuman(report.movementTotals())));
    }
    if (!report.closingTotals().isEmpty()) {
      rows.add(List.of("Closing totals", joinedBalancesHuman(report.closingTotals())));
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
        HUMAN_TABLE_WIDTH,
        List.of(
            "Line code",
            "Line name",
            "Role",
            "Classification",
            "Kind",
            "Currency",
            "Opening",
            "Movement",
            "Closing",
            "Closing side"),
        rows.stream()
            .map(
                row ->
                    List.of(
                        CliQueryOutputFormatter.displayStatementLineCode(
                            row.lineCode(), row.lineKind()),
                        row.lineName(),
                        CliQueryOutputFormatter.displayLineRole(row.lineRole()),
                        CliQueryOutputFormatter.displayFinancialPositionLineClassification(
                            row.lineClassification()),
                        CliQueryOutputFormatter.displayRowKind(row.lineKind()),
                        row.closingBalance().netAmount().currencyUnit().code(),
                        CliQueryOutputFormatter.displayMoney(row.openingBalance().netAmount()),
                        CliQueryOutputFormatter.displayMoney(row.movement().netAmount()),
                        CliQueryOutputFormatter.displayMoney(row.closingBalance().netAmount()),
                        CliQueryOutputFormatter.displayBalanceSideLabel(
                            row.closingBalance().balanceSide())))
            .toList(),
        5,
        6,
        7);
  }

  private static String comparativeChangesInEquityTotals(ChangesInEquityReport report) {
    List<List<String>> rows = new java.util.ArrayList<>();
    if (!report.comparativeOpeningTotals().isEmpty()) {
      rows.add(
          List.of(
              "Comparative opening totals",
              joinedBalancesHuman(report.comparativeOpeningTotals())));
    }
    if (!report.comparativeMovementTotals().isEmpty()) {
      rows.add(
          List.of(
              "Comparative movement totals",
              joinedBalancesHuman(report.comparativeMovementTotals())));
    }
    if (!report.comparativeClosingTotals().isEmpty()) {
      rows.add(
          List.of(
              "Comparative closing totals",
              joinedBalancesHuman(report.comparativeClosingTotals())));
    }
    return rows.isEmpty() ? "" : CliTextFormat.renderKeyValueBlock(List.copyOf(rows));
  }
}
