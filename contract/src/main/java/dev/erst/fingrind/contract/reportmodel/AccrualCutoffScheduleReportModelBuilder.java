package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleReport;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleRow;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.ArrayList;
import java.util.List;

/** Builds the shared report model for durable accrual cut-off lifecycle balances. */
public final class AccrualCutoffScheduleReportModelBuilder
    implements ReportModelBuilder<AccrualCutoffScheduleReport> {
  private static final String REPORT_FAMILY = OperationId.ACCRUAL_CUTOFF_SCHEDULE.wireName();
  private static final List<String> CSV_HEADERS =
      List.of(
          "exportFamily",
          "rowId",
          "accrualCutoffId",
          "kind",
          "originatedOn",
          "cutoffAccountCode",
          "recognitionAccountCode",
          "originalAmountCurrencyCode",
          "originalAmountMinorUnits",
          "appliedAmountCurrencyCode",
          "appliedAmountMinorUnits",
          "remainingAmountCurrencyCode",
          "remainingAmountMinorUnits",
          "recognitionStartDate",
          "recognitionEndDate",
          "latestApplicationEffectiveDate",
          "message");

  /** Shared reusable builder instance. */
  public static final AccrualCutoffScheduleReportModelBuilder INSTANCE =
      new AccrualCutoffScheduleReportModelBuilder();

  private static final LifecycleRegisterReportModelSupport.SingleSectionRegisterProjection<
          AccrualCutoffScheduleReport, AccrualCutoffScheduleRow>
      PROJECTION =
          new LifecycleRegisterReportModelSupport.SingleSectionRegisterProjection<>(
              REPORT_FAMILY,
              ReportModelSupport.reportTitle(OperationId.ACCRUAL_CUTOFF_SCHEDULE),
              "Lifecycle truth",
              "Original amount less append-only recognition or settlement applications",
              "Cut-offs",
              "accrual-cutoffs",
              "Accrual cut-offs",
              "accrual cut-offs",
              AccrualCutoffScheduleReport::bookIdentity,
              AccrualCutoffScheduleReport::effectiveDateAsOf,
              AccrualCutoffScheduleReport::rows,
              List.of(
                  ReportModelSupport.leftColumn("accrualCutoffId", "Cut-off"),
                  ReportModelSupport.leftColumn("kind", "Kind"),
                  ReportModelSupport.leftColumn("originatedOn", "Originated on"),
                  ReportModelSupport.leftColumn("cutoffAccountCode", "Balance account"),
                  ReportModelSupport.leftColumn("recognitionAccountCode", "Recognition account"),
                  ReportModelSupport.rightColumn("originalAmount", "Original"),
                  ReportModelSupport.rightColumn("appliedAmount", "Applied"),
                  ReportModelSupport.rightColumn("remainingAmount", "Remaining"),
                  ReportModelSupport.leftColumn("recognitionInterval", "Recognition interval"),
                  ReportModelSupport.leftColumn(
                      "latestApplicationEffectiveDate", "Lifecycle horizon")),
              AccrualCutoffScheduleReportModelBuilder::row,
              AccrualCutoffScheduleReportModelBuilder::csvProjection);

  private AccrualCutoffScheduleReportModelBuilder() {}

  @Override
  public ReportModel build(AccrualCutoffScheduleReport report) {
    return buildModel(report);
  }

  /** Builds one accrual cut-off schedule report model. */
  public static ReportModel buildModel(AccrualCutoffScheduleReport report) {
    return LifecycleRegisterReportModelSupport.build(report, PROJECTION);
  }

  private static ReportRow row(AccrualCutoffScheduleRow row) {
    String interval =
        row.recognitionStartDate()
            .map(start -> start + " through " + row.recognitionEndDate().orElseThrow())
            .orElse("Not applicable");
    return ReportModelSupport.row(
        row.accrualCutoffId().value(),
        row.accrualCutoffId().value(),
        row.kind().wireValue(),
        row.originatedOn().toString(),
        row.cutoffAccountCode().value(),
        row.recognitionAccountCode().value(),
        ReportModelDisplay.displayAmount(row.originalAmount()),
        ReportModelDisplay.displayAmount(row.appliedAmount()),
        ReportModelDisplay.displayAmount(row.remainingAmount()),
        interval,
        row.latestApplicationEffectiveDate().map(Object::toString).orElse("None"));
  }

  private static ReportCsvProjection csvProjection(AccrualCutoffScheduleReport report) {
    if (report.rows().isEmpty()) {
      return new ReportCsvProjection(
          CSV_HEADERS,
          List.of(
              List.of(
                  REPORT_FAMILY,
                  REPORT_FAMILY + ":scope-empty",
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
                  "",
                  "",
                  ReportModelNarrative.noMatches("accrual cut-offs"))));
    }
    List<List<String>> rows = new ArrayList<>();
    report.rows().forEach(row -> rows.add(csvRow(row)));
    return new ReportCsvProjection(CSV_HEADERS, List.copyOf(rows));
  }

  private static List<String> csvRow(AccrualCutoffScheduleRow row) {
    return List.of(
        REPORT_FAMILY,
        REPORT_FAMILY + ":" + row.accrualCutoffId().value(),
        row.accrualCutoffId().value(),
        row.kind().wireValue(),
        row.originatedOn().toString(),
        row.cutoffAccountCode().value(),
        row.recognitionAccountCode().value(),
        row.originalAmount().currencyCode(),
        row.originalAmount().minorUnits(),
        row.appliedAmount().currencyCode(),
        row.appliedAmount().minorUnits(),
        row.remainingAmount().currencyCode(),
        row.remainingAmount().minorUnits(),
        row.recognitionStartDate().map(Object::toString).orElse(""),
        row.recognitionEndDate().map(Object::toString).orElse(""),
        row.latestApplicationEffectiveDate().map(Object::toString).orElse(""),
        "");
  }
}
