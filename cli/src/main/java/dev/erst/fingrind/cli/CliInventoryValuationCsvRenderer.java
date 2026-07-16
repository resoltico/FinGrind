package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliInventoryReportJsonModels;
import dev.erst.fingrind.cli.json.CliReportValueJsonModels;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Renders the typed CSV row table for inventory valuation. */
final class CliInventoryValuationCsvRenderer {
  private static final List<String> HEADERS =
      List.of(
          "family",
          "inventoryAccountCode",
          "inventoryAccountName",
          "unitOfMeasure",
          "quantityScale",
          "quantityOnHandScaledUnits",
          "carryingValueCurrencyCode",
          "carryingValueMinorUnits",
          "roundedMovingAverageUnitCostProjectionCurrencyCode",
          "roundedMovingAverageUnitCostProjectionMinorUnits",
          "movementPostingId",
          "movementEffectiveDate",
          "movementAccountSequence",
          "movementKind",
          "movementQuantityDeltaScaledUnits",
          "movementCostDeltaMinor");

  private CliInventoryValuationCsvRenderer() {}

  static String render(CliInventoryReportJsonModels.InventoryValuationPayload report) {
    List<List<String>> rows = new ArrayList<>();
    for (CliInventoryReportJsonModels.InventoryValuationRowPayload row : report.rows()) {
      if (row.movements().isEmpty()) {
        appendSnapshotRow(rows, report.family(), row);
        continue;
      }
      for (CliInventoryReportJsonModels.InventoryMovementPayload movement : row.movements()) {
        appendMovementRow(rows, report.family(), row, movement);
      }
    }
    return CliTextFormat.renderCsv(HEADERS, rows);
  }

  private static void appendSnapshotRow(
      List<List<String>> target,
      String family,
      CliInventoryReportJsonModels.InventoryValuationRowPayload row) {
    List<String> values = baseRow(family, row);
    addBlankMovementColumns(values);
    target.add(List.copyOf(values));
  }

  private static void appendMovementRow(
      List<List<String>> target,
      String family,
      CliInventoryReportJsonModels.InventoryValuationRowPayload row,
      CliInventoryReportJsonModels.InventoryMovementPayload movement) {
    List<String> values = baseRow(family, row);
    values.add(movement.postingId());
    values.add(movement.effectiveDate());
    values.add(movement.accountSequence());
    values.add(movement.kind());
    values.add(movement.quantityDeltaScaledUnits());
    values.add(movement.costDeltaMinor());
    target.add(List.copyOf(values));
  }

  private static List<String> baseRow(
      String family, CliInventoryReportJsonModels.InventoryValuationRowPayload row) {
    List<String> values = new ArrayList<>();
    values.add(family);
    values.add(row.inventoryAccountCode());
    values.add(row.inventoryAccountName());
    values.add(row.unitOfMeasure());
    values.add(Integer.toString(row.quantityScale()));
    values.add(row.quantityOnHandScaledUnits());
    values.add(row.carryingValue().currencyCode());
    values.add(row.carryingValue().minorUnits());
    addOptionalMoney(values, row.roundedMovingAverageUnitCostProjection());
    return values;
  }

  private static void addOptionalMoney(
      List<String> values, CliReportValueJsonModels.@Nullable MoneyPayload money) {
    if (money == null) {
      values.add("");
      values.add("");
      return;
    }
    values.add(money.currencyCode());
    values.add(money.minorUnits());
  }

  private static void addBlankMovementColumns(List<String> values) {
    for (int index = 0; index < 6; index++) {
      values.add("");
    }
  }
}
