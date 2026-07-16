package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.discovery.ContractReversalTemplates.ReversalTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractSettlementTemplates.TaxSelectionTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.JournalLineTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.OpeningBalanceTemplateDescriptor;
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

  static void requirePresent(@Nullable Object value, String fieldName) {
    if (value == null) {
      throw new IllegalArgumentException(fieldName + " is required for this template.");
    }
  }

  static void requireAbsent(@Nullable Object value, String fieldName) {
    if (value != null) {
      throw new IllegalArgumentException(fieldName + " must be absent for this template.");
    }
  }
}
