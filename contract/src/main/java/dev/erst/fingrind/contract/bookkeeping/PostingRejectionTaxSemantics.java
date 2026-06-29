package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import java.util.Objects;

/** Canonical owner for tax-specific posting entry-semantics rejection details. */
final class PostingRejectionTaxSemantics {
  private PostingRejectionTaxSemantics() {}

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  static PostingRejection.EntrySemanticsViolation unknownTaxRegistration(
      String selectorValue, TaxRegistrationId taxRegistrationId) {
    return unknownTaxRegistration("entryKind", selectorValue, taxRegistrationId);
  }

  /** Returns one entry-semantics violation for an unknown tax registration reference. */
  static PostingRejection.EntrySemanticsViolation unknownTaxRegistration(
      String selectorField, String selectorValue, TaxRegistrationId taxRegistrationId) {
    String requiredSelectorField =
        ContractDescriptorValidation.requireText(selectorField, "selectorField");
    String requiredSelectorValue =
        ContractDescriptorValidation.requireText(selectorValue, "selectorValue");
    Objects.requireNonNull(taxRegistrationId, "taxRegistrationId");
    return new PostingRejection.EntrySemanticsViolation(
        "unknown-tax-registration",
        "tax.taxRegistrationId",
        "%s '%s' references tax.taxRegistrationId '%s', but that registration is not declared in this book."
            .formatted(requiredSelectorField, requiredSelectorValue, taxRegistrationId.value()));
  }

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  static PostingRejection.EntrySemanticsViolation unknownTaxCode(
      String selectorValue, TaxRegistrationId taxRegistrationId, TaxCode taxCode) {
    return unknownTaxCode("entryKind", selectorValue, taxRegistrationId, taxCode);
  }

  /** Returns one entry-semantics violation for an unknown tax code on one registration. */
  static PostingRejection.EntrySemanticsViolation unknownTaxCode(
      String selectorField,
      String selectorValue,
      TaxRegistrationId taxRegistrationId,
      TaxCode taxCode) {
    String requiredSelectorField =
        ContractDescriptorValidation.requireText(selectorField, "selectorField");
    String requiredSelectorValue =
        ContractDescriptorValidation.requireText(selectorValue, "selectorValue");
    Objects.requireNonNull(taxRegistrationId, "taxRegistrationId");
    Objects.requireNonNull(taxCode, "taxCode");
    return new PostingRejection.EntrySemanticsViolation(
        "unknown-tax-code",
        "tax.taxCode",
        "%s '%s' references tax.taxCode '%s', but registration '%s' does not declare that code."
            .formatted(
                requiredSelectorField,
                requiredSelectorValue,
                taxCode.value(),
                taxRegistrationId.value()));
  }

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  static PostingRejection.EntrySemanticsViolation taxApplicationKindMismatch(
      String selectorValue,
      TaxCode taxCode,
      TaxApplicationKind expectedApplicationKind,
      TaxApplicationKind actualApplicationKind) {
    return taxApplicationKindMismatch(
        "entryKind", selectorValue, taxCode, expectedApplicationKind, actualApplicationKind);
  }

  /** Returns one entry-semantics violation for a mismatched tax application kind. */
  static PostingRejection.EntrySemanticsViolation taxApplicationKindMismatch(
      String selectorField,
      String selectorValue,
      TaxCode taxCode,
      TaxApplicationKind expectedApplicationKind,
      TaxApplicationKind actualApplicationKind) {
    String requiredSelectorField =
        ContractDescriptorValidation.requireText(selectorField, "selectorField");
    String requiredSelectorValue =
        ContractDescriptorValidation.requireText(selectorValue, "selectorValue");
    Objects.requireNonNull(taxCode, "taxCode");
    Objects.requireNonNull(expectedApplicationKind, "expectedApplicationKind");
    Objects.requireNonNull(actualApplicationKind, "actualApplicationKind");
    return new PostingRejection.EntrySemanticsViolation(
        "tax-application-kind-mismatch",
        "tax.taxCode",
        "%s '%s' requires tax.taxCode '%s' to resolve to applicationKind '%s', but the declared applicationKind is '%s'."
            .formatted(
                requiredSelectorField,
                requiredSelectorValue,
                taxCode.value(),
                expectedApplicationKind.wireValue(),
                actualApplicationKind.wireValue()));
  }
}
