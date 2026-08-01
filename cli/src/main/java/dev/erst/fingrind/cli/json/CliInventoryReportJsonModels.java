package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Semantic machine payloads for inventory valuation. */
public interface CliInventoryReportJsonModels {
  record InventoryValuationPayload(
      String family,
      CliBookInspectionJsonModels.BookIdentityPayload bookIdentity,
      CliReportJsonModels.InventoryValuationResolvedQuery resolvedQuery,
      String generatedAt,
      List<InventoryValuationRowPayload> rows)
      implements CliReportJsonModels.ReportPayload {
    public InventoryValuationPayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      rows = copyList(rows, "rows");
    }
  }

  record InventoryValuationRowPayload(
      String inventoryAccountCode,
      String inventoryAccountName,
      String unitOfMeasure,
      int quantityScale,
      String quantityOnHandScaledUnits,
      CliReportValueJsonModels.MoneyPayload carryingValue,
      CliReportValueJsonModels.@Nullable MoneyPayload roundedMovingAverageUnitCostProjection,
      List<InventoryMovementPayload> movements) {
    public InventoryValuationRowPayload {
      inventoryAccountCode = requireText(inventoryAccountCode, "inventoryAccountCode");
      inventoryAccountName = requireText(inventoryAccountName, "inventoryAccountName");
      unitOfMeasure = requireText(unitOfMeasure, "unitOfMeasure");
      quantityOnHandScaledUnits =
          requireText(quantityOnHandScaledUnits, "quantityOnHandScaledUnits");
      Objects.requireNonNull(carryingValue, "carryingValue");
      movements = copyList(movements, "movements");
    }
  }

  record InventoryMovementPayload(
      String postingId,
      String effectiveDate,
      String accountSequence,
      String kind,
      String quantityDeltaScaledUnits,
      String costDeltaMinor) {
    public InventoryMovementPayload {
      postingId = requireText(postingId, "postingId");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      accountSequence = requireText(accountSequence, "accountSequence");
      kind = requireText(kind, "kind");
      quantityDeltaScaledUnits = requireText(quantityDeltaScaledUnits, "quantityDeltaScaledUnits");
      costDeltaMinor = requireText(costDeltaMinor, "costDeltaMinor");
    }
  }
}
