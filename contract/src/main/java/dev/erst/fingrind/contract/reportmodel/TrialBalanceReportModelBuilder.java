package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import java.util.ArrayList;
import java.util.List;

/** Builds the shared report model for trial-balance reports. */
public final class TrialBalanceReportModelBuilder
    implements ReportModelBuilder<TrialBalanceReport> {
  /** Shared reusable builder instance. */
  public static final TrialBalanceReportModelBuilder INSTANCE =
      new TrialBalanceReportModelBuilder();

  private TrialBalanceReportModelBuilder() {}

  @Override
  public ReportModel build(TrialBalanceReport report) {
    return buildModel(report);
  }

  /** Builds one trial-balance report model. */
  public static ReportModel buildModel(TrialBalanceReport report) {
    List<ReportSection> sections = new ArrayList<>();
    sections.add(
        ReportModelSupport.section(
            "current",
            "Accounts",
            report.rows().isEmpty()
                ? List.of(
                    new ReportVerdict(
                        "Outcome", ReportModelNarrative.noMatches("account balances")))
                : List.of(),
            accountColumns(),
            report.rows().stream().map(TrialBalanceReportModelBuilder::accountRow).toList(),
            report.totals().isEmpty()
                ? List.of()
                : List.of(
                    ReportModelSupport.totals(
                        "currentTotals",
                        "Current totals",
                        ReportModelSupport.balanceColumns(),
                        ReportModelSupport.balanceRows(report.totals())))));
    if (!report.comparativeRows().isEmpty() || !report.comparativeTotals().isEmpty()) {
      sections.add(
          ReportModelSupport.section(
              "comparative",
              "Comparative Trial Balance",
              List.of(
                  new ReportVerdict(
                      "As of",
                      report
                          .comparativeEffectiveDateRange()
                          .effectiveDateTo()
                          .map(java.time.LocalDate::toString)
                          .orElse("(none)")),
                  new ReportVerdict(
                      "Comparative range",
                      ReportModelNarrative.comparativeRange(
                          report.comparativeEffectiveDateRange())),
                  new ReportVerdict(
                      "Balance state",
                      ReportModelDisplay.displayBalanceState(report.comparativeBalanced()))),
              accountColumns(),
              report.comparativeRows().stream()
                  .map(TrialBalanceReportModelBuilder::accountRow)
                  .toList(),
              report.comparativeTotals().isEmpty()
                  ? List.of()
                  : List.of(
                      ReportModelSupport.totals(
                          "comparativeTotals",
                          "Comparative totals",
                          ReportModelSupport.balanceColumns(),
                          ReportModelSupport.balanceRows(report.comparativeTotals())))));
    }
    return new ReportModel(
        dev.erst.fingrind.contract.protocol.OperationId.TRIAL_BALANCE.wireName(),
        ReportModelSupport.reportTitle(
            dev.erst.fingrind.contract.protocol.OperationId.TRIAL_BALANCE),
        ReportModel.Orientation.LANDSCAPE,
        ReportModelSupport.context(
            report.bookIdentity(),
            report.postingCoverage(),
            null,
            null,
            report.resolvedEffectiveDateAsOf().orElse(null),
            report.comparativeEffectiveDateRange(),
            List.of()),
        List.of(
            new ReportVerdict(
                "As of",
                report
                    .resolvedEffectiveDateAsOf()
                    .map(java.time.LocalDate::toString)
                    .orElse("(none)")),
            new ReportVerdict(
                "Balance state", ReportModelDisplay.displayBalanceState(report.balanced()))),
        List.copyOf(sections));
  }

  private static List<ReportColumn> accountColumns() {
    return List.of(
        ReportModelSupport.leftColumn("accountCode", "Account"),
        ReportModelSupport.leftColumn("accountName", "Name"),
        ReportModelSupport.leftColumn("currency", "Currency"),
        ReportModelSupport.rightColumn("debitTotal", "Debit total"),
        ReportModelSupport.rightColumn("creditTotal", "Credit total"),
        ReportModelSupport.rightColumn("netAmount", "Net amount"),
        ReportModelSupport.leftColumn("balanceSide", "Balance side"));
  }

  private static ReportRow accountRow(TrialBalanceRow row) {
    return ReportModelSupport.row(
        row.account().accountCode().value() + ":" + row.balance().netAmount().currencyUnit().code(),
        row.account().accountCode().value(),
        row.account().accountName().value(),
        row.balance().netAmount().currencyUnit().code(),
        ReportModelDisplay.displayMoney(row.balance().debitTotal()),
        ReportModelDisplay.displayMoney(row.balance().creditTotal()),
        ReportModelDisplay.displayMoney(row.balance().netAmount()),
        ReportModelDisplay.displayBalanceSide(row.balance().balanceSide()));
  }
}
