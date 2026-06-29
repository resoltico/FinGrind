package dev.erst.fingrind.contract.tax;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Durable applied-tax fact resolved from one declared registration and code. */
public record AppliedTax(
    TaxRegistrationId taxRegistrationId,
    TaxCode taxCode,
    TaxCodeName taxCodeName,
    TaxRate rate,
    TaxInclusionMode inclusionMode,
    TaxApplicationKind applicationKind,
    MonetaryAmount taxableAmount,
    MonetaryAmount taxAmount,
    MonetaryAmount grossAmount,
    @Nullable AccountCode taxAccountCode) {
  /** Validates one durable applied-tax fact. */
  public AppliedTax {
    Objects.requireNonNull(taxRegistrationId, "taxRegistrationId");
    Objects.requireNonNull(taxCode, "taxCode");
    Objects.requireNonNull(taxCodeName, "taxCodeName");
    Objects.requireNonNull(rate, "rate");
    Objects.requireNonNull(inclusionMode, "inclusionMode");
    Objects.requireNonNull(applicationKind, "applicationKind");
    Objects.requireNonNull(taxableAmount, "taxableAmount");
    Objects.requireNonNull(taxAmount, "taxAmount");
    Objects.requireNonNull(grossAmount, "grossAmount");
    if (!taxableAmount.currencyCode().equals(taxAmount.currencyCode())
        || !taxableAmount.currencyCode().equals(grossAmount.currencyCode())) {
      throw new IllegalArgumentException("Applied tax amounts must all use the same currencyCode.");
    }
  }
}
