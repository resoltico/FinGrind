package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import java.util.ArrayList;
import java.util.List;

/** Builds the shared report model for period-summary reports. */
public final class PeriodSummaryReportModelBuilder
    implements ReportModelBuilder<PeriodSummaryReport> {
  /** Shared reusable builder instance. */
  public static final PeriodSummaryReportModelBuilder INSTANCE =
      new PeriodSummaryReportModelBuilder();

  private PeriodSummaryReportModelBuilder() {}

  @Override
  public ReportModel build(PeriodSummaryReport report) {
    return buildModel(report);
  }

  /** Builds one period-summary report model. */
  public static ReportModel buildModel(PeriodSummaryReport report) {
    List<ReportSection> sections = new ArrayList<>();
    sections.add(
        ReportModelSupport.section(
            "currencyTotals",
            "Currency Totals",
            report.currencyTotals().isEmpty()
                ? List.of(
                    new ReportVerdict("Outcome", ReportModelNarrative.noMatches("currency totals")))
                : List.of(),
            ReportModelSupport.balanceColumns(),
            report.currencyTotals().stream()
                .map(
                    summary -> ReportModelSupport.balanceRows(List.of(summary.totals())).getFirst())
                .toList(),
            List.of()));
    sections.add(
        ReportModelSupport.section(
            "accountActivity",
            "Account Activity",
            report.accountActivity().isEmpty()
                ? List.of(
                    new ReportVerdict(
                        "Outcome", ReportModelNarrative.noMatches("account activity")))
                : List.of(),
            activityColumns(),
            report.accountActivity().stream()
                .map(
                    row ->
                        ReportModelSupport.row(
                            row.account().accountCode().value()
                                + ":"
                                + row.movement().netAmount().currencyUnit().code(),
                            row.account().accountCode().value(),
                            row.account().accountName().value(),
                            ReportModelDisplay.displayLineType(row.account().accountType()),
                            ReportModelDisplay.displayNormalBalance(row.account().normalBalance()),
                            ReportModelDisplay.displayBoolean(row.account().active()),
                            row.movement().netAmount().currencyUnit().code(),
                            ReportModelDisplay.displayMoney(row.movement().debitTotal()),
                            ReportModelDisplay.displayMoney(row.movement().creditTotal()),
                            ReportModelDisplay.displayMoney(row.movement().netAmount()),
                            ReportModelDisplay.displayBalanceSide(row.movement().balanceSide())))
                .toList(),
            List.of()));
    return new ReportModel(
        dev.erst.fingrind.contract.protocol.OperationId.PERIOD_SUMMARY.wireName(),
        "Period Summary",
        ReportModel.Orientation.LANDSCAPE,
        ReportModelSupport.context(
            report.bookIdentity(),
            report.postingCoverage(),
            report.effectiveDateFrom(),
            report.effectiveDateTo(),
            null,
            dev.erst.fingrind.core.EffectiveDateRange.unbounded(),
            List.of()),
        List.of(
            new ReportVerdict("Period start", report.effectiveDateFrom().toString()),
            new ReportVerdict("Period end", report.effectiveDateTo().toString()),
            new ReportVerdict("Posting count", Integer.toString(report.postingCount())),
            new ReportVerdict("Posting line count", Integer.toString(report.postingLineCount())),
            new ReportVerdict("Accounts touched", Integer.toString(report.accountsTouched()))),
        List.copyOf(sections));
  }

  private static List<ReportColumn> activityColumns() {
    return List.of(
        ReportModelSupport.leftColumn("accountCode", "Account"),
        ReportModelSupport.leftColumn("accountName", "Name"),
        ReportModelSupport.leftColumn("accountType", "Type"),
        ReportModelSupport.leftColumn("normalBalance", "Normal"),
        ReportModelSupport.leftColumn("active", "Active"),
        ReportModelSupport.leftColumn("currency", "Currency"),
        ReportModelSupport.rightColumn("debitTotal", "Debit total"),
        ReportModelSupport.rightColumn("creditTotal", "Credit total"),
        ReportModelSupport.rightColumn("netAmount", "Net amount"),
        ReportModelSupport.leftColumn("balanceSide", "Balance side"));
  }
}
