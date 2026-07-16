package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.QuantityText;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import org.jspecify.annotations.Nullable;

/** Owns scalar amount, quantity, and unit-cost validation for posting request templates. */
final class ContractPostingTemplateScalarFieldRules {
  private ContractPostingTemplateScalarFieldRules() {}

  static MonetaryAmount requirePositiveAmount(@Nullable MonetaryAmount amount) {
    return requirePositiveMoney(amount, "amount");
  }

  static String requirePositiveQuantity(@Nullable String quantity) {
    if (quantity == null) {
      throw new IllegalArgumentException("quantity must not be null.");
    }
    String requiredQuantity = ContractDescriptorValidation.requireText(quantity, "quantity");
    QuantityText quantityText = new QuantityText(requiredQuantity);
    if (quantityText.isZero()) {
      throw new IllegalArgumentException("quantity must carry one positive quantity.");
    }
    return requiredQuantity;
  }

  static MonetaryAmount requirePositiveUnitCost(@Nullable MonetaryAmount unitCost) {
    return requirePositiveMoney(unitCost, "unitCost");
  }

  static void forbidAmount(@Nullable MonetaryAmount amount, String entryKindName) {
    forbid(amount, "amount", entryKindName);
  }

  static void forbidQuantity(@Nullable String quantity, String entryKindName) {
    forbid(quantity, "quantity", entryKindName);
  }

  static void forbidUnitCost(@Nullable MonetaryAmount unitCost, String entryKindName) {
    forbid(unitCost, "unitCost", entryKindName);
  }

  private static MonetaryAmount requirePositiveMoney(
      @Nullable MonetaryAmount value, String fieldName) {
    if (value == null) {
      throw new IllegalArgumentException(fieldName + " must not be null.");
    }
    MonetaryAmount requiredAmount = ContractDescriptorValidation.requireValue(value, fieldName);
    if (!requiredAmount.toMoney().isPositive()) {
      throw new IllegalArgumentException(fieldName + " must carry one positive minor-unit value.");
    }
    return requiredAmount;
  }

  private static void forbid(@Nullable Object value, String fieldName, String entryKindName) {
    if (value != null) {
      throw new IllegalArgumentException(fieldName + " must be absent for " + entryKindName + ".");
    }
  }
}
