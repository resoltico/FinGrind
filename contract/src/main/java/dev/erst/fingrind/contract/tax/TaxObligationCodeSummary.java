package dev.erst.fingrind.contract.tax;

import dev.erst.fingrind.contract.bookkeeping.SignedMonetaryAmount;
import java.util.Objects;

/** One per-code summary row inside a tax-obligation report. */
public record TaxObligationCodeSummary(
    TaxCode taxCode,
    TaxCodeName taxCodeName,
    TaxApplicationKind applicationKind,
    int postingCount,
    SignedMonetaryAmount taxableAmount,
    SignedMonetaryAmount taxAmount,
    SignedMonetaryAmount grossAmount) {
  /** Validates one tax-obligation code summary. */
  public TaxObligationCodeSummary {
    Objects.requireNonNull(taxCode, "taxCode");
    Objects.requireNonNull(taxCodeName, "taxCodeName");
    Objects.requireNonNull(applicationKind, "applicationKind");
    Objects.requireNonNull(taxableAmount, "taxableAmount");
    Objects.requireNonNull(taxAmount, "taxAmount");
    Objects.requireNonNull(grossAmount, "grossAmount");
    if (postingCount < 0) {
      throw new IllegalArgumentException("postingCount must not be negative.");
    }
  }
}
