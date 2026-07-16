package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterRow;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds the shared report model for durable financing principal and interest reconciliation. */
public final class FinancingRegisterReportModelBuilder
    implements ReportModelBuilder<FinancingRegisterReport> {
  private static final String REPORT_FAMILY = OperationId.FINANCING_REGISTER.wireName();
  private static final List<String> CSV_HEADERS =
      List.of(
          "exportFamily",
          "rowId",
          "financingArrangementId",
          "originatedOn",
          "lifecycleHorizon",
          "principalLiabilityAccountCode",
          "interestPayableAccountCode",
          "originalPrincipalCurrencyCode",
          "originalPrincipalMinorUnits",
          "principalRepaidCurrencyCode",
          "principalRepaidMinorUnits",
          "principalOutstandingCurrencyCode",
          "principalOutstandingMinorUnits",
          "interestAccruedCurrencyCode",
          "interestAccruedMinorUnits",
          "interestPaidCurrencyCode",
          "interestPaidMinorUnits",
          "interestOutstandingCurrencyCode",
          "interestOutstandingMinorUnits",
          "message");

  /** Shared builder instance. */
  public static final FinancingRegisterReportModelBuilder INSTANCE =
      new FinancingRegisterReportModelBuilder();

  private static final LifecycleRegisterReportModelSupport.SingleSectionRegisterProjection<
          FinancingRegisterReport, FinancingRegisterRow>
      PROJECTION =
          new LifecycleRegisterReportModelSupport.SingleSectionRegisterProjection<>(
              REPORT_FAMILY,
              ReportModelSupport.reportTitle(OperationId.FINANCING_REGISTER),
              "Register truth",
              "Immutable borrowing and compensating lifecycle facts",
              "Financing arrangements",
              "financing-arrangements",
              "Financing arrangements",
              "financing arrangements",
              FinancingRegisterReport::bookIdentity,
              ignored -> Optional.empty(),
              FinancingRegisterReport::rows,
              List.of(
                  ReportModelSupport.leftColumn("financingArrangementId", "Arrangement"),
                  ReportModelSupport.leftColumn("originatedOn", "Originated"),
                  ReportModelSupport.rightColumn("principalOutstanding", "Principal outstanding"),
                  ReportModelSupport.rightColumn("interestOutstanding", "Interest outstanding"),
                  ReportModelSupport.leftColumn("lifecycleHorizon", "Lifecycle horizon")),
              FinancingRegisterReportModelBuilder::row,
              FinancingRegisterReportModelBuilder::csvProjection);

  private FinancingRegisterReportModelBuilder() {}

  @Override
  public ReportModel build(FinancingRegisterReport report) {
    return buildModel(report);
  }

  /** Builds one financing register report model. */
  public static ReportModel buildModel(FinancingRegisterReport report) {
    return LifecycleRegisterReportModelSupport.build(report, PROJECTION);
  }

  private static ReportRow row(FinancingRegisterRow row) {
    return ReportModelSupport.row(
        row.financingArrangementId().value(),
        row.financingArrangementId().value(),
        row.originatedOn().toString(),
        ReportModelDisplay.displayAmount(row.principalOutstanding()),
        ReportModelDisplay.displayAmount(row.interestOutstanding()),
        row.lifecycleHorizon().toString());
  }

  private static ReportCsvProjection csvProjection(FinancingRegisterReport report) {
    List<List<String>> rows = new ArrayList<>();
    if (report.rows().isEmpty()) {
      rows.add(
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
              "",
              "",
              "",
              ReportModelNarrative.noMatches("financing arrangements")));
    } else {
      report.rows().forEach(row -> rows.add(csvRow(row)));
    }
    return new ReportCsvProjection(CSV_HEADERS, List.copyOf(rows));
  }

  private static List<String> csvRow(FinancingRegisterRow row) {
    return List.of(
        REPORT_FAMILY,
        REPORT_FAMILY + ":" + row.financingArrangementId().value(),
        row.financingArrangementId().value(),
        row.originatedOn().toString(),
        row.lifecycleHorizon().toString(),
        row.principalLiabilityAccountCode().value(),
        row.interestPayableAccountCode().value(),
        row.originalPrincipal().currencyCode(),
        row.originalPrincipal().minorUnits(),
        row.principalRepaid().currencyCode(),
        row.principalRepaid().minorUnits(),
        row.principalOutstanding().currencyCode(),
        row.principalOutstanding().minorUnits(),
        row.interestAccrued().currencyCode(),
        row.interestAccrued().minorUnits(),
        row.interestPaid().currencyCode(),
        row.interestPaid().minorUnits(),
        row.interestOutstanding().currencyCode(),
        row.interestOutstanding().minorUnits(),
        "");
  }
}
