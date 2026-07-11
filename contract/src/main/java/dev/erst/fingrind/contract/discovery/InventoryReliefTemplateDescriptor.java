package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.QuantityText;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountCode;

/** Canonical inventory-relief template nested inside one trading-sale request. */
public record InventoryReliefTemplateDescriptor(
    String inventoryAccountCode, String costOfSalesAccountCode, String quantity)
    implements TemplateDescriptorType {
  /** Validates one inventory-relief template descriptor payload. */
  public InventoryReliefTemplateDescriptor {
    inventoryAccountCode =
        ContractDescriptorValidation.requireText(inventoryAccountCode, "inventoryAccountCode");
    costOfSalesAccountCode =
        ContractDescriptorValidation.requireText(costOfSalesAccountCode, "costOfSalesAccountCode");
    quantity = ContractDescriptorValidation.requireText(quantity, "quantity");
    new AccountCode(inventoryAccountCode);
    new AccountCode(costOfSalesAccountCode);
    QuantityText quantityText = new QuantityText(quantity);
    if (quantityText.isZero()) {
      throw new IllegalArgumentException("quantity must carry one positive quantity.");
    }
  }
}
