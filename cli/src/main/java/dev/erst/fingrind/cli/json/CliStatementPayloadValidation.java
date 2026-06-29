package dev.erst.fingrind.cli.json;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels.BalanceBucketPayload;
import dev.erst.fingrind.cli.json.CliReportSupportJsonModels.ReportContextPayload;
import java.util.List;

/** Shared validation for statement-oriented CLI payload records. */
final class CliStatementPayloadValidation {
  private CliStatementPayloadValidation() {}

  static StatementWindow requireStatementWindow(
      String effectiveDateFrom, String effectiveDateTo, ReportContextPayload context) {
    return new StatementWindow(
        CliJsonModelValidation.requireText(effectiveDateFrom, "effectiveDateFrom"),
        CliJsonModelValidation.requireText(effectiveDateTo, "effectiveDateTo"),
        CliJsonModelValidation.requireValue(context, "context"));
  }

  static ComparativeBalanceBuckets copyComparativeBalanceBuckets(
      List<BalanceBucketPayload> openingTotals,
      List<BalanceBucketPayload> movementTotals,
      List<BalanceBucketPayload> closingTotals,
      List<BalanceBucketPayload> comparativeOpeningTotals,
      List<BalanceBucketPayload> comparativeMovementTotals,
      List<BalanceBucketPayload> comparativeClosingTotals) {
    return new ComparativeBalanceBuckets(
        CliJsonModelValidation.copyList(openingTotals, "openingTotals"),
        CliJsonModelValidation.copyList(movementTotals, "movementTotals"),
        CliJsonModelValidation.copyList(closingTotals, "closingTotals"),
        CliJsonModelValidation.copyList(comparativeOpeningTotals, "comparativeOpeningTotals"),
        CliJsonModelValidation.copyList(comparativeMovementTotals, "comparativeMovementTotals"),
        CliJsonModelValidation.copyList(comparativeClosingTotals, "comparativeClosingTotals"));
  }

  record StatementWindow(
      String effectiveDateFrom, String effectiveDateTo, ReportContextPayload context) {}

  record ComparativeBalanceBuckets(
      List<BalanceBucketPayload> openingTotals,
      List<BalanceBucketPayload> movementTotals,
      List<BalanceBucketPayload> closingTotals,
      List<BalanceBucketPayload> comparativeOpeningTotals,
      List<BalanceBucketPayload> comparativeMovementTotals,
      List<BalanceBucketPayload> comparativeClosingTotals) {}
}
