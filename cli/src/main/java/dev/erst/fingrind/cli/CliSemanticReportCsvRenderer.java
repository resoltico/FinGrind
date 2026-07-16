package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAccountReportJsonModels;
import dev.erst.fingrind.cli.json.CliAccrualCutoffReportJsonModels;
import dev.erst.fingrind.cli.json.CliFixedAssetReportJsonModels;
import dev.erst.fingrind.cli.json.CliInventoryReportJsonModels;
import dev.erst.fingrind.cli.json.CliLatvianPayrollReportJsonModels;
import dev.erst.fingrind.cli.json.CliLifecycleContextReportJsonModels;
import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.cli.json.CliStatementReportJsonModels;
import dev.erst.fingrind.cli.json.CliTaxReportJsonModels;

/** Dispatches each semantic report family to its single typed CSV table renderer. */
final class CliSemanticReportCsvRenderer {
  private CliSemanticReportCsvRenderer() {}

  static String render(CliReportJsonModels.ReportPayload payload) {
    return switch (payload) {
      case CliAccountReportJsonModels.AccountReportPayload report ->
          CliAccountReportCsvRenderer.render(report);
      case CliStatementReportJsonModels.StatementReportPayload report ->
          CliStatementReportCsvRenderer.render(report);
      case CliInventoryReportJsonModels.InventoryValuationPayload report ->
          CliInventoryValuationCsvRenderer.render(report);
      case CliAccrualCutoffReportJsonModels.AccrualCutoffSchedulePayload report ->
          CliAccrualCutoffScheduleCsvRenderer.render(report);
      case CliFixedAssetReportJsonModels.FixedAssetRegisterPayload report ->
          CliFixedAssetRegisterCsvRenderer.render(report);
      case CliLifecycleContextReportJsonModels.LifecycleContextReportPayload report ->
          CliLifecycleContextRegisterCsvRenderer.render(report);
      case CliLatvianPayrollReportJsonModels.LatvianPayrollRegisterPayload report ->
          CliLatvianPayrollRegisterCsvRenderer.render(report);
      case CliTaxReportJsonModels.TaxObligationPayload report ->
          CliTaxObligationCsvRenderer.render(report);
    };
  }
}
