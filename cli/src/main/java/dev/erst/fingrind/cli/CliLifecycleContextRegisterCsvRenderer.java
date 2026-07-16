package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliLifecycleContextReportJsonModels;
import java.util.List;

/** Renders tabular CSV for Financing and Realized Foreign Exchange register payloads. */
final class CliLifecycleContextRegisterCsvRenderer {
  private CliLifecycleContextRegisterCsvRenderer() {}

  static String render(CliLifecycleContextReportJsonModels.LifecycleContextReportPayload report) {
    return switch (report) {
      case CliLifecycleContextReportJsonModels.FinancingRegisterPayload financing ->
          render(financing);
      case CliLifecycleContextReportJsonModels.RealizedForeignExchangeRegisterPayload realizedFx ->
          render(realizedFx);
    };
  }

  static String render(CliLifecycleContextReportJsonModels.FinancingRegisterPayload report) {
    List<String> headers =
        List.of(
            "exportFamily",
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
            "interestOutstandingMinorUnits");
    return CliTextFormat.renderCsv(
        headers,
        report.rows().stream()
            .map(
                row ->
                    List.of(
                        report.family(),
                        row.financingArrangementId(),
                        row.originatedOn(),
                        row.lifecycleHorizon(),
                        row.principalLiabilityAccountCode(),
                        row.interestPayableAccountCode(),
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
                        row.interestOutstanding().minorUnits()))
            .toList());
  }

  static String render(
      CliLifecycleContextReportJsonModels.RealizedForeignExchangeRegisterPayload report) {
    List<String> headers =
        List.of(
            "exportFamily",
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
            "realizedResult");
    return CliTextFormat.renderCsv(
        headers,
        report.rows().stream()
            .map(
                row ->
                    List.of(
                        report.family(),
                        row.foreignCurrencyObligationId(),
                        row.originatedOn(),
                        row.lifecycleHorizon(),
                        row.receivableAccountCode(),
                        row.transactionAmount().currencyCode(),
                        row.transactionAmount().minorUnits(),
                        row.functionalCarryingAmount().currencyCode(),
                        row.functionalCarryingAmount().minorUnits(),
                        row.settledOn() == null ? "" : row.settledOn(),
                        row.functionalSettlementAmount() == null
                            ? ""
                            : row.functionalSettlementAmount().currencyCode(),
                        row.functionalSettlementAmount() == null
                            ? ""
                            : row.functionalSettlementAmount().minorUnits(),
                        row.realizedGainOrLossAmount() == null
                            ? ""
                            : row.realizedGainOrLossAmount().currencyCode(),
                        row.realizedGainOrLossAmount() == null
                            ? ""
                            : row.realizedGainOrLossAmount().minorUnits(),
                        row.realizedGain() == null ? "" : row.realizedGain() ? "gain" : "loss"))
            .toList());
  }
}
