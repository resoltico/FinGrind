package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Tax-resolution validation shared by caller-authored bookkeeping entries. */
final class BookkeepingEntryTaxValidationSupport {
  private BookkeepingEntryTaxValidationSupport() {}

  static void requireTaxSelectionState(
      MonetaryAmount operatorAmount,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax,
      TaxApplicationKind... allowedApplicationKinds) {
    Objects.requireNonNull(operatorAmount, "operatorAmount");
    if (taxSelection == null) {
      if (appliedTax != null) {
        throw new IllegalArgumentException("appliedTax requires one matching taxSelection.");
      }
      return;
    }
    Objects.requireNonNull(taxSelection, "taxSelection");
    if (appliedTax == null) {
      return;
    }
    if (!taxSelection.taxRegistrationId().equals(appliedTax.taxRegistrationId())
        || !taxSelection.taxCode().equals(appliedTax.taxCode())) {
      throw new IllegalArgumentException(
          "appliedTax must match the selected taxRegistrationId and taxCode.");
    }
    boolean allowed = false;
    for (TaxApplicationKind allowedApplicationKind : allowedApplicationKinds) {
      if (appliedTax.applicationKind() == allowedApplicationKind) {
        allowed = true;
        break;
      }
    }
    if (!allowed) {
      throw new IllegalArgumentException(
          "appliedTax applicationKind is not supported by this entry kind.");
    }
    requireResolvedOperatorAmount(operatorAmount, appliedTax);
  }

  static AppliedTax requireResolvedAppliedTax(@Nullable AppliedTax appliedTax, String entryKind) {
    if (appliedTax == null) {
      throw new IllegalStateException(
          entryKind
              + " tax selection requires executor-owned tax resolution before journalEntry() can be derived.");
    }
    return appliedTax;
  }

  static AccountCode requireTaxAccountCode(AppliedTax appliedTax, String entryKind) {
    if (appliedTax.taxAccountCode() == null) {
      throw new IllegalArgumentException(
          entryKind + " appliedTax must carry taxAccountCode when taxAmount is positive.");
    }
    return appliedTax.taxAccountCode();
  }

  private static void requireResolvedOperatorAmount(
      MonetaryAmount operatorAmount, AppliedTax appliedTax) {
    String amountCurrency = operatorAmount.currencyCode();
    if (!amountCurrency.equals(appliedTax.taxableAmount().currencyCode())) {
      throw new IllegalArgumentException(
          "appliedTax taxableAmount currencyCode must match the entry amount currencyCode.");
    }
    if (appliedTax.inclusionMode() == TaxInclusionMode.EXCLUSIVE) {
      if (!operatorAmount.equals(appliedTax.taxableAmount())) {
        throw new IllegalArgumentException(
            "Exclusive tax entries must retain the operator-supplied amount as the taxable amount.");
      }
      return;
    }
    if (!operatorAmount.equals(appliedTax.grossAmount())) {
      throw new IllegalArgumentException(
          "Inclusive tax entries must retain the operator-supplied amount as the gross amount.");
    }
  }
}
