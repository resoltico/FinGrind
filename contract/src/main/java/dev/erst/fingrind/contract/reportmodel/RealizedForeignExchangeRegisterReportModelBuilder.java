package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterRow;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds the shared report model for foreign-currency carrying and realized settlement results. */
public final class RealizedForeignExchangeRegisterReportModelBuilder
    implements ReportModelBuilder<RealizedForeignExchangeRegisterReport> {
  private static final String REPORT_FAMILY =
      OperationId.REALIZED_FOREIGN_EXCHANGE_REGISTER.wireName();
  private static final List<String> CSV_HEADERS =
      List.of(
          "exportFamily",
          "rowId",
          "foreignCurrencyObligationId",
          "originatedOn",
          "lifecycleHorizon",
          "receivableAccountCode",
          "transactionCurrencyCode",
          "transactionMinorUnits",
          "functionalCarryingCurrencyCode",
          "functionalCarryingMinorUnits",
          "settledOn",
          "functionalSettlementCurrencyCode",
          "functionalSettlementMinorUnits",
          "realizedGainOrLossCurrencyCode",
          "realizedGainOrLossMinorUnits",
          "realizedResult",
          "message");

  /** Shared builder instance. */
  public static final RealizedForeignExchangeRegisterReportModelBuilder INSTANCE =
      new RealizedForeignExchangeRegisterReportModelBuilder();

  private static final LifecycleRegisterReportModelSupport.SingleSectionRegisterProjection<
          RealizedForeignExchangeRegisterReport, RealizedForeignExchangeRegisterRow>
      PROJECTION =
          new LifecycleRegisterReportModelSupport.SingleSectionRegisterProjection<>(
              REPORT_FAMILY,
              ReportModelSupport.reportTitle(OperationId.REALIZED_FOREIGN_EXCHANGE_REGISTER),
              "Register truth",
              "Immutable foreign-currency receivables and compensating settlements",
              "Foreign-currency obligations",
              "foreign-currency-obligations",
              "Foreign-currency obligations",
              "foreign-currency obligations",
              RealizedForeignExchangeRegisterReport::bookIdentity,
              ignored -> Optional.empty(),
              RealizedForeignExchangeRegisterReport::rows,
              List.of(
                  ReportModelSupport.leftColumn("foreignCurrencyObligationId", "Obligation"),
                  ReportModelSupport.leftColumn("originatedOn", "Originated"),
                  ReportModelSupport.rightColumn("functionalCarryingAmount", "Carrying value"),
                  ReportModelSupport.rightColumn("realizedGainOrLoss", "Realized result"),
                  ReportModelSupport.leftColumn("settledOn", "Settlement")),
              RealizedForeignExchangeRegisterReportModelBuilder::row,
              RealizedForeignExchangeRegisterReportModelBuilder::csvProjection);

  private RealizedForeignExchangeRegisterReportModelBuilder() {}

  @Override
  public ReportModel build(RealizedForeignExchangeRegisterReport report) {
    return buildModel(report);
  }

  /** Builds one realized foreign-exchange register report model. */
  public static ReportModel buildModel(RealizedForeignExchangeRegisterReport report) {
    return LifecycleRegisterReportModelSupport.build(report, PROJECTION);
  }

  private static ReportRow row(RealizedForeignExchangeRegisterRow row) {
    return ReportModelSupport.row(
        row.foreignCurrencyObligationId().value(),
        row.foreignCurrencyObligationId().value(),
        row.originatedOn().toString(),
        ReportModelDisplay.displayAmount(row.functionalCarryingAmount()),
        row.realizedGainOrLossAmount().map(ReportModelDisplay::displayAmount).orElse(""),
        row.settledOn().map(Object::toString).orElse("Open"));
  }

  private static ReportCsvProjection csvProjection(RealizedForeignExchangeRegisterReport report) {
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
              ReportModelNarrative.noMatches("foreign-currency obligations")));
    } else {
      report.rows().forEach(row -> rows.add(csvRow(row)));
    }
    return new ReportCsvProjection(CSV_HEADERS, List.copyOf(rows));
  }

  private static List<String> csvRow(RealizedForeignExchangeRegisterRow row) {
    return List.of(
        REPORT_FAMILY,
        REPORT_FAMILY + ":" + row.foreignCurrencyObligationId().value(),
        row.foreignCurrencyObligationId().value(),
        row.originatedOn().toString(),
        row.lifecycleHorizon().toString(),
        row.receivableAccountCode().value(),
        row.transactionAmount().currencyCode(),
        row.transactionAmount().minorUnits(),
        row.functionalCarryingAmount().currencyCode(),
        row.functionalCarryingAmount().minorUnits(),
        row.settledOn().map(Object::toString).orElse(""),
        row.functionalSettlementAmount().map(amount -> amount.currencyCode()).orElse(""),
        row.functionalSettlementAmount().map(amount -> amount.minorUnits()).orElse(""),
        row.realizedGainOrLossAmount().map(amount -> amount.currencyCode()).orElse(""),
        row.realizedGainOrLossAmount().map(amount -> amount.minorUnits()).orElse(""),
        row.realizedGain().map(gain -> gain ? "gain" : "loss").orElse(""),
        "");
  }
}
