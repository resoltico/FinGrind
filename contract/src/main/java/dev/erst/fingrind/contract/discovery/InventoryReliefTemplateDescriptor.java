package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountCode;

/** Canonical inventory-relief template nested inside one trading-sale request. */
public record InventoryReliefTemplateDescriptor(
    String inventoryAccountCode, String costOfSalesAccountCode, MonetaryAmount amount)
    implements TemplateDescriptorType {
  /** Validates one inventory-relief template descriptor payload. */
  public InventoryReliefTemplateDescriptor {
    inventoryAccountCode =
        ContractDescriptorValidation.requireText(inventoryAccountCode, "inventoryAccountCode");
    costOfSalesAccountCode =
        ContractDescriptorValidation.requireText(costOfSalesAccountCode, "costOfSalesAccountCode");
    new AccountCode(inventoryAccountCode);
    new AccountCode(costOfSalesAccountCode);
    amount = ContractDescriptorValidation.requireValue(amount, "amount");
    if (!amount.toMoney().isPositive()) {
      throw new IllegalArgumentException("amount must carry one positive minor-unit value.");
    }
  }
}
