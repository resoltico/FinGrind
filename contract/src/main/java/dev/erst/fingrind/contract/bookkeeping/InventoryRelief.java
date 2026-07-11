package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.util.Objects;

/** Trading-sale inventory relief facts paired with one typed sale entry. */
public record InventoryRelief(
    AccountCode inventoryAccountCode, AccountCode costOfSalesAccountCode, QuantityText quantity) {
  /** Validates one inventory-relief fact bundle. */
  public InventoryRelief {
    Objects.requireNonNull(inventoryAccountCode, "inventoryAccountCode");
    Objects.requireNonNull(costOfSalesAccountCode, "costOfSalesAccountCode");
    quantity =
        BookkeepingEntryScalarValidationSupport.requirePositiveQuantityText(
            quantity, "inventoryRelief.quantity");
  }
}
