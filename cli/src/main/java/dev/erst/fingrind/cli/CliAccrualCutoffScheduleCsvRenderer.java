package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAccrualCutoffReportJsonModels;
import java.util.List;

/** Renders the typed tabular CSV projection for the accrual cut-off schedule. */
final class CliAccrualCutoffScheduleCsvRenderer {
  private static final List<String> HEADERS =
      List.of(
          "family",
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
          "latestApplicationEffectiveDate");

  private CliAccrualCutoffScheduleCsvRenderer() {}

  static String render(CliAccrualCutoffReportJsonModels.AccrualCutoffSchedulePayload report) {
    return CliTextFormat.renderCsv(
        HEADERS,
        report.rows().stream()
            .map(
                row ->
                    List.of(
                        report.family(),
                        row.accrualCutoffId(),
                        row.kind(),
                        row.originatedOn(),
                        row.cutoffAccountCode(),
                        row.recognitionAccountCode(),
                        row.originalAmount().currencyCode(),
                        row.originalAmount().minorUnits(),
                        row.appliedAmount().currencyCode(),
                        row.appliedAmount().minorUnits(),
                        row.remainingAmount().currencyCode(),
                        row.remainingAmount().minorUnits(),
                        row.recognitionStartDate() == null ? "" : row.recognitionStartDate(),
                        row.recognitionEndDate() == null ? "" : row.recognitionEndDate(),
                        row.latestApplicationEffectiveDate() == null
                            ? ""
                            : row.latestApplicationEffectiveDate()))
            .toList());
  }
}
