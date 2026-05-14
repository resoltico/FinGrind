package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical identity metadata for one accounting entity book. */
public record BookIdentity(
    BookEntityName entityName, CurrencyUnit functionalCurrency, FiscalYearStart fiscalYearStart) {
  /** Validates one book identity. */
  public BookIdentity {
    Objects.requireNonNull(entityName, "entityName");
    Objects.requireNonNull(functionalCurrency, "functionalCurrency");
    Objects.requireNonNull(fiscalYearStart, "fiscalYearStart");
  }
}
