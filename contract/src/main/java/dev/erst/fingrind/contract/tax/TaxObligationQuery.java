package dev.erst.fingrind.contract.tax;

import java.time.LocalDate;
import java.util.Objects;

/** Required bounded-period request for one tax-obligation report. */
public record TaxObligationQuery(
    TaxRegistrationId taxRegistrationId, LocalDate effectiveDateFrom, LocalDate effectiveDateTo) {
  /** Validates one tax-obligation query. */
  public TaxObligationQuery {
    Objects.requireNonNull(taxRegistrationId, "taxRegistrationId");
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
  }
}
