package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliInventoryReportJsonModels;
import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationAccount;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationMovement;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationReport;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.time.Instant;

/** Projects inventory valuation into its semantic machine payload. */
final class CliInventoryReportPayloadMapper {
  private CliInventoryReportPayloadMapper() {}

  static CliInventoryReportJsonModels.InventoryValuationPayload inventoryValuation(
      InventoryValuationReport report, Instant generatedAt) {
    return new CliInventoryReportJsonModels.InventoryValuationPayload(
        CliReportPayloadMappingSupport.family(OperationId.INVENTORY_VALUATION),
        CliReportPayloadMappingSupport.bookIdentity(report.bookIdentity()),
        new CliReportJsonModels.InventoryValuationResolvedQuery(
            CliReportPayloadMappingSupport.date(report.effectiveDateAsOf().orElse(null)),
            report.includesMovements()),
        CliReportPayloadMappingSupport.instant(generatedAt),
        report.accounts().stream()
            .map(CliInventoryReportPayloadMapper::inventoryValuationRow)
            .toList());
  }

  private static CliInventoryReportJsonModels.InventoryValuationRowPayload inventoryValuationRow(
      InventoryValuationAccount row) {
    return new CliInventoryReportJsonModels.InventoryValuationRowPayload(
        row.inventoryAccountCode().value(),
        row.inventoryAccountName().value(),
        row.unitOfMeasure().token(),
        row.unitOfMeasure().quantityScale(),
        Long.toString(row.quantityOnHand().scaledUnits()),
        CliReportPayloadMappingSupport.money(row.carryingValue()),
        row.roundedMovingAverageUnitCostProjection() == null
            ? null
            : CliReportPayloadMappingSupport.money(row.roundedMovingAverageUnitCostProjection()),
        row.movements().stream().map(CliInventoryReportPayloadMapper::inventoryMovement).toList());
  }

  private static CliInventoryReportJsonModels.InventoryMovementPayload inventoryMovement(
      InventoryValuationMovement movement) {
    return new CliInventoryReportJsonModels.InventoryMovementPayload(
        movement.postingId().value(),
        movement.effectiveDate().toString(),
        Long.toString(movement.accountSequence()),
        movement.kind().name(),
        Long.toString(movement.quantityDeltaScaledUnits()),
        Long.toString(movement.costDeltaMinor()));
  }
}
