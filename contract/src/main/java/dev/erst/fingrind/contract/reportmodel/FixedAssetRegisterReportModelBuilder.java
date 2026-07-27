package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterRow;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.ArrayList;
import java.util.List;

/** Builds the shared report model for fixed-asset lifecycle reconciliation. */
public final class FixedAssetRegisterReportModelBuilder
    implements ReportModelBuilder<FixedAssetRegisterReport> {
  private static final String REPORT_FAMILY = OperationId.FIXED_ASSET_REGISTER.wireName();
  private static final List<String> CSV_HEADERS =
      List.of(
          "exportFamily",
          "rowId",
          "fixedAssetId",
          "capitalizedOn",
          "assetAccountCode",
          "accumulatedDepreciationAccountCode",
          "costCurrencyCode",
          "costMinorUnits",
          "accumulatedDepreciationCurrencyCode",
          "accumulatedDepreciationMinorUnits",
          "carryingAmountCurrencyCode",
          "carryingAmountMinorUnits",
          "carryingAmountAtDisposalCurrencyCode",
          "carryingAmountAtDisposalMinorUnits",
          "inServiceDate",
          "usefulLifeMonths",
          "residualValueCurrencyCode",
          "residualValueMinorUnits",
          "depreciationPeriodsApplied",
          "latestLifecycleEffectiveDate",
          "disposedOn",
          "message");

  /** Shared builder instance. */
  public static final FixedAssetRegisterReportModelBuilder INSTANCE =
      new FixedAssetRegisterReportModelBuilder();

  private static final LifecycleRegisterReportModelSupport.SingleSectionRegisterProjection<
          FixedAssetRegisterReport, FixedAssetRegisterRow>
      PROJECTION =
          new LifecycleRegisterReportModelSupport.SingleSectionRegisterProjection<>(
              REPORT_FAMILY,
              ReportModelSupport.reportTitle(OperationId.FIXED_ASSET_REGISTER),
              "Lifecycle truth",
              "Immutable capitalization and compensating lifecycle facts",
              "Fixed assets",
              "fixed-assets",
              "Fixed assets",
              "fixed assets",
              FixedAssetRegisterReport::bookIdentity,
              FixedAssetRegisterReport::effectiveDateAsOf,
              FixedAssetRegisterReport::rows,
              List.of(
                  ReportModelSupport.leftColumn("fixedAssetId", "Asset"),
                  ReportModelSupport.leftColumn("capitalizedOn", "Capitalized"),
                  ReportModelSupport.rightColumn("cost", "Cost"),
                  ReportModelSupport.rightColumn(
                      "accumulatedDepreciation", "Accumulated depreciation"),
                  ReportModelSupport.rightColumn("carryingAmount", "Carrying value"),
                  ReportModelSupport.rightColumn(
                      "carryingAmountAtDisposal", "Carrying before disposal"),
                  ReportModelSupport.leftColumn("disposedOn", "Disposed")),
              FixedAssetRegisterReportModelBuilder::row,
              FixedAssetRegisterReportModelBuilder::csvProjection);

  private FixedAssetRegisterReportModelBuilder() {}

  @Override
  public ReportModel build(FixedAssetRegisterReport report) {
    return buildModel(report);
  }

  /** Builds one fixed-asset register report model. */
  public static ReportModel buildModel(FixedAssetRegisterReport report) {
    return LifecycleRegisterReportModelSupport.build(report, PROJECTION);
  }

  private static ReportRow row(FixedAssetRegisterRow r) {
    return ReportModelSupport.row(
        r.fixedAssetId().value(),
        r.fixedAssetId().value(),
        r.capitalizedOn().toString(),
        ReportModelDisplay.displayAmount(r.cost()),
        ReportModelDisplay.displayAmount(r.accumulatedDepreciation()),
        ReportModelDisplay.displayAmount(r.carryingAmount()),
        r.carryingAmountAtDisposal().map(ReportModelDisplay::displayAmount).orElse(""),
        r.disposedOn().map(Object::toString).orElse("Active"));
  }

  private static ReportCsvProjection csvProjection(FixedAssetRegisterReport report) {
    List<List<String>> rows = new ArrayList<>();
    if (report.rows().isEmpty())
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
              "",
              "",
              ReportModelNarrative.noMatches("fixed assets")));
    else report.rows().forEach(r -> rows.add(csvRow(r)));
    return new ReportCsvProjection(CSV_HEADERS, List.copyOf(rows));
  }

  private static List<String> csvRow(FixedAssetRegisterRow r) {
    return List.of(
        REPORT_FAMILY,
        REPORT_FAMILY + ":" + r.fixedAssetId().value(),
        r.fixedAssetId().value(),
        r.capitalizedOn().toString(),
        r.assetAccountCode().value(),
        r.accumulatedDepreciationAccountCode().value(),
        r.cost().currencyCode(),
        r.cost().minorUnits(),
        r.accumulatedDepreciation().currencyCode(),
        r.accumulatedDepreciation().minorUnits(),
        r.carryingAmount().currencyCode(),
        r.carryingAmount().minorUnits(),
        r.carryingAmountAtDisposal().map(MonetaryAmount::currencyCode).orElse(""),
        r.carryingAmountAtDisposal().map(MonetaryAmount::minorUnits).orElse(""),
        r.depreciationSchedule().inServiceDate().toString(),
        Integer.toString(r.depreciationSchedule().usefulLifeMonths()),
        r.depreciationSchedule().residualValue().currencyCode(),
        r.depreciationSchedule().residualValue().minorUnits(),
        Integer.toString(r.depreciationPeriodsApplied()),
        r.latestLifecycleEffectiveDate().map(Object::toString).orElse(""),
        r.disposedOn().map(Object::toString).orElse(""),
        "");
  }
}
