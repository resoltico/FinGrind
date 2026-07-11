package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.QuantityText;
import dev.erst.fingrind.contract.discovery.ContractReversalTemplates.ReversalTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.JournalLineTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.OpeningBalanceTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.TaxSelectionTemplateDescriptor;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared field-presence rules for posting-request template variants. */
final class ContractPostingTemplateFieldRules {
  private ContractPostingTemplateFieldRules() {}

  static String requireText(@Nullable String value, String fieldName) {
    if (value == null) {
      throw new IllegalArgumentException(fieldName + " must not be null.");
    }
    return ContractDescriptorValidation.requireText(value, fieldName);
  }

  static void forbidText(@Nullable String value, String fieldName) {
    if (value != null) {
      throw new IllegalArgumentException(fieldName + " must be absent for this entryKind.");
    }
  }

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
    if (amount != null) {
      throw new IllegalArgumentException("amount must be absent for " + entryKindName + ".");
    }
  }

  static void forbidQuantity(@Nullable String quantity, String entryKindName) {
    if (quantity != null) {
      throw new IllegalArgumentException("quantity must be absent for " + entryKindName + ".");
    }
  }

  static void forbidUnitCost(@Nullable MonetaryAmount unitCost, String entryKindName) {
    if (unitCost != null) {
      throw new IllegalArgumentException("unitCost must be absent for " + entryKindName + ".");
    }
  }

  static void forbidLines(@Nullable List<JournalLineTemplateDescriptor> lines) {
    if (lines != null) {
      throw new IllegalArgumentException("lines must be absent for this entryKind.");
    }
  }

  static void requireLines(
      @Nullable List<JournalLineTemplateDescriptor> lines, String entryKindName) {
    if (lines == null || lines.size() < 2) {
      throw new IllegalArgumentException(
          "lines must contain at least two journal lines for " + entryKindName + ".");
    }
  }

  static void forbidOpeningBalances(
      @Nullable List<OpeningBalanceTemplateDescriptor> openingBalances) {
    if (openingBalances != null) {
      throw new IllegalArgumentException("openingBalances must be absent for this entryKind.");
    }
  }

  static void forbidTax(@Nullable TaxSelectionTemplateDescriptor tax, String entryKindName) {
    if (tax != null) {
      throw new IllegalArgumentException("tax must be absent for " + entryKindName + ".");
    }
  }

  static void forbidForeignExchange(
      @Nullable ForeignExchangeTemplateDescriptor foreignExchange, String entryKindName) {
    if (foreignExchange != null) {
      throw new IllegalArgumentException(
          "foreignExchange must be absent for " + entryKindName + ".");
    }
  }

  static void forbidReversal(@Nullable ReversalTemplateDescriptor reversal) {
    if (reversal != null) {
      throw new IllegalArgumentException("reversal must be absent for this entryKind.");
    }
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
}
