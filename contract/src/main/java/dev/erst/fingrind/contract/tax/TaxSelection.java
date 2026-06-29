package dev.erst.fingrind.contract.tax;

import java.util.Objects;

/** Typed request-side selection of one declared tax rule. */
public record TaxSelection(TaxRegistrationId taxRegistrationId, TaxCode taxCode) {
  /** Validates one request-side tax selection. */
  public TaxSelection {
    Objects.requireNonNull(taxRegistrationId, "taxRegistrationId");
    Objects.requireNonNull(taxCode, "taxCode");
  }
}
