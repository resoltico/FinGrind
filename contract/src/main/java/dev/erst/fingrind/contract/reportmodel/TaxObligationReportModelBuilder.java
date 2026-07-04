package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.tax.TaxObligationReport;
import java.util.List;

/** Builds the shared report model for tax-obligation reports. */
public final class TaxObligationReportModelBuilder
    implements ReportModelBuilder<TaxObligationReport> {
  /** Shared reusable builder instance. */
  public static final TaxObligationReportModelBuilder INSTANCE =
      new TaxObligationReportModelBuilder();

  private TaxObligationReportModelBuilder() {}

  @Override
  public ReportModel build(TaxObligationReport report) {
    return buildModel(report);
  }

  /** Builds one tax-obligation report model. */
  public static ReportModel buildModel(TaxObligationReport report) {
    return new ReportModel(
        dev.erst.fingrind.contract.protocol.OperationId.TAX_OBLIGATION.wireName(),
        "Tax Obligation",
        ReportModel.Orientation.LANDSCAPE,
        ReportModelSupport.taxContext(
            report.bookIdentity(),
            report.registration().taxRegistrationId().value(),
            report.registration().taxRegistrationName().value(),
            report.registration().jurisdiction().value(),
            report.reportingPeriod().effectiveDateFrom(),
            report.reportingPeriod().effectiveDateTo(),
            report.dueDate()),
        List.of(
            new ReportVerdict("Output tax", ReportModelDisplay.displayAmount(report.outputTax())),
            new ReportVerdict(
                "Recoverable input tax",
                ReportModelDisplay.displayAmount(report.recoverableInputTax())),
            new ReportVerdict(
                "Nonrecoverable input tax",
                ReportModelDisplay.displayAmount(report.nonrecoverableInputTax())),
            new ReportVerdict("Net payable", ReportModelDisplay.displayAmount(report.netPayable())),
            new ReportVerdict(
                "Net receivable", ReportModelDisplay.displayAmount(report.netReceivable()))),
        List.of(
            ReportModelSupport.section(
                "codeSummaries",
                "Code summaries",
                report.codeSummaries().isEmpty()
                    ? List.of(
                        new ReportVerdict(
                            "Outcome",
                            ReportModelNarrative.noMatches("tax obligation code summaries")))
                    : List.of(),
                sectionColumns(),
                report.codeSummaries().stream()
                    .map(
                        summary ->
                            ReportModelSupport.row(
                                summary.taxCode().value()
                                    + ":"
                                    + summary.applicationKind().wireValue(),
                                summary.taxCode().value(),
                                summary.taxCodeName().value(),
                                summary.applicationKind().wireValue(),
                                Integer.toString(summary.postingCount()),
                                ReportModelDisplay.displayAmount(summary.taxableAmount()),
                                ReportModelDisplay.displayAmount(summary.taxAmount()),
                                ReportModelDisplay.displayAmount(summary.grossAmount())))
                    .toList(),
                List.of(
                    ReportModelSupport.totals(
                        "netPosition",
                        "Net position",
                        List.of(
                            ReportModelSupport.leftColumn("metric", "Metric"),
                            ReportModelSupport.leftColumn("value", "Value")),
                        List.of(
                            ReportModelSupport.row(
                                "outputTax",
                                "Output tax",
                                ReportModelDisplay.displayAmount(report.outputTax())),
                            ReportModelSupport.row(
                                "recoverableInputTax",
                                "Recoverable input tax",
                                ReportModelDisplay.displayAmount(report.recoverableInputTax())),
                            ReportModelSupport.row(
                                "nonrecoverableInputTax",
                                "Nonrecoverable input tax",
                                ReportModelDisplay.displayAmount(report.nonrecoverableInputTax())),
                            ReportModelSupport.row(
                                "netPayable",
                                "Net payable",
                                ReportModelDisplay.displayAmount(report.netPayable())),
                            ReportModelSupport.row(
                                "netReceivable",
                                "Net receivable",
                                ReportModelDisplay.displayAmount(report.netReceivable()))))))));
  }

  private static List<ReportColumn> sectionColumns() {
    return List.of(
        ReportModelSupport.leftColumn("taxCode", "Tax code"),
        ReportModelSupport.leftColumn("taxCodeName", "Name"),
        ReportModelSupport.leftColumn("applicationKind", "Application"),
        ReportModelSupport.rightColumn("postingCount", "Postings"),
        ReportModelSupport.rightColumn("taxableAmount", "Taxable"),
        ReportModelSupport.rightColumn("taxAmount", "Tax"),
        ReportModelSupport.rightColumn("grossAmount", "Gross"));
  }
}
