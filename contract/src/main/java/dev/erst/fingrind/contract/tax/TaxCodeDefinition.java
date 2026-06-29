package dev.erst.fingrind.contract.tax;

import java.util.Objects;

/** One declared tax code inside one registration. */
public record TaxCodeDefinition(
    TaxCode taxCode,
    TaxCodeName taxCodeName,
    TaxRate rate,
    TaxInclusionMode inclusionMode,
    TaxApplicationKind applicationKind) {
  /** Validates one declared tax-code definition. */
  public TaxCodeDefinition {
    Objects.requireNonNull(taxCode, "taxCode");
    Objects.requireNonNull(taxCodeName, "taxCodeName");
    Objects.requireNonNull(rate, "rate");
    Objects.requireNonNull(inclusionMode, "inclusionMode");
    Objects.requireNonNull(applicationKind, "applicationKind");
  }
}
