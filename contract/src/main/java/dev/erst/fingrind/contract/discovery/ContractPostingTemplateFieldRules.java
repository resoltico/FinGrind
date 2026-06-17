package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.JournalRecipeKind;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractTemplates.JournalLineTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.OpeningBalanceTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.ReversalTemplateDescriptor;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookkeepingEntryKind;
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
    if (amount == null) {
      throw new IllegalArgumentException("amount must not be null.");
    }
    MonetaryAmount requiredAmount = ContractDescriptorValidation.requireValue(amount, "amount");
    if (!requiredAmount.toMoney().isPositive()) {
      throw new IllegalArgumentException("amount must carry one positive minor-unit value.");
    }
    return requiredAmount;
  }

  static void forbidAmount(@Nullable MonetaryAmount amount, String entryKindName) {
    if (amount != null) {
      throw new IllegalArgumentException("amount must be absent for " + entryKindName + ".");
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

  static void forbidReversal(@Nullable ReversalTemplateDescriptor reversal) {
    if (reversal != null) {
      throw new IllegalArgumentException("reversal must be absent for this entryKind.");
    }
  }

  static void forbidRecipeKind(
      @Nullable JournalRecipeKind recipeKind, BookkeepingEntryKind entryKind) {
    if (recipeKind != null) {
      throw new IllegalArgumentException(
          "recipeKind must be absent for " + entryKind.wireValue() + ".");
    }
  }
}
