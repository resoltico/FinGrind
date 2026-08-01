package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliFixedAssetReportJsonModels;
import java.util.List;

/** Renders the typed tabular CSV projection for the fixed-asset register. */
final class CliFixedAssetRegisterCsvRenderer {
  private static final List<String> HEADERS =
      List.of(
          "family",
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
          "disposedOn");

  private CliFixedAssetRegisterCsvRenderer() {}

  static String render(CliFixedAssetReportJsonModels.FixedAssetRegisterPayload report) {
    return CliTextFormat.renderCsv(
        HEADERS,
        report.rows().stream()
            .map(
                row ->
                    List.of(
                        report.family(),
                        row.fixedAssetId(),
                        row.capitalizedOn(),
                        row.assetAccountCode(),
                        row.accumulatedDepreciationAccountCode(),
                        row.cost().currencyCode(),
                        row.cost().minorUnits(),
                        row.accumulatedDepreciation().currencyCode(),
                        row.accumulatedDepreciation().minorUnits(),
                        row.carryingAmount().currencyCode(),
                        row.carryingAmount().minorUnits(),
                        row.carryingAmountAtDisposal() == null
                            ? ""
                            : row.carryingAmountAtDisposal().currencyCode(),
                        row.carryingAmountAtDisposal() == null
                            ? ""
                            : row.carryingAmountAtDisposal().minorUnits(),
                        row.inServiceDate(),
                        Integer.toString(row.usefulLifeMonths()),
                        row.residualValue().currencyCode(),
                        row.residualValue().minorUnits(),
                        Integer.toString(row.depreciationPeriodsApplied()),
                        row.latestLifecycleEffectiveDate() == null
                            ? ""
                            : row.latestLifecycleEffectiveDate(),
                        row.disposedOn() == null ? "" : row.disposedOn()))
            .toList());
  }
}
