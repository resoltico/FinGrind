package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.PostingRejectionSemantics;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import java.util.Objects;

/** Executor-local tax-semantics violations owned by tax selection validation. */
public final class BookkeepingTaxSemanticsViolations {
  private BookkeepingTaxSemanticsViolations() {}

  /** Creates one unknown-tax-registration violation for one explicit selector pair. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation unknownTaxRegistration(
      String selectorField, String selectorValue, TaxRegistrationId taxRegistrationId) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    Objects.requireNonNull(taxRegistrationId, "taxRegistrationId");
    return new BookkeepingPostingRejection.EntrySemanticsViolation(
        "unknown-tax-registration",
        "tax.taxRegistrationId",
        "%s '%s' references tax.taxRegistrationId '%s', but that registration is not declared in this book."
            .formatted(
                selectorField,
                BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
                taxRegistrationId.value()));
  }

  /** Creates one unknown-tax-code violation for one explicit selector pair. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation unknownTaxCode(
      String selectorField,
      String selectorValue,
      TaxRegistrationId taxRegistrationId,
      TaxCode taxCode) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    Objects.requireNonNull(taxRegistrationId, "taxRegistrationId");
    Objects.requireNonNull(taxCode, "taxCode");
    return new BookkeepingPostingRejection.EntrySemanticsViolation(
        "unknown-tax-code",
        "tax.taxCode",
        "%s '%s' references tax.taxCode '%s', but registration '%s' does not declare that code."
            .formatted(
                selectorField,
                BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
                taxCode.value(),
                taxRegistrationId.value()));
  }

  /** Creates one tax-application-kind mismatch violation for one explicit selector pair. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation taxApplicationKindMismatch(
      String selectorField,
      String selectorValue,
      TaxCode taxCode,
      TaxApplicationKind expectedApplicationKind,
      TaxApplicationKind actualApplicationKind) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    Objects.requireNonNull(taxCode, "taxCode");
    Objects.requireNonNull(expectedApplicationKind, "expectedApplicationKind");
    Objects.requireNonNull(actualApplicationKind, "actualApplicationKind");
    return new BookkeepingPostingRejection.EntrySemanticsViolation(
        "tax-application-kind-mismatch",
        "tax.taxCode",
        "%s '%s' requires tax.taxCode '%s' to resolve to applicationKind '%s', but the declared applicationKind is '%s'."
            .formatted(
                selectorField,
                BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
                taxCode.value(),
                expectedApplicationKind.wireValue(),
                actualApplicationKind.wireValue()));
  }

  /**
   * Creates the rejection for a tax calculation whose derived monetary facts cannot be represented.
   */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      taxCompositionMoneyRangeExceeded(String selectorValue) {
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.taxCompositionMoneyRangeExceeded(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue)));
  }
}
