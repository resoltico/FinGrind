package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical identity metadata for one accounting entity book. */
public record BookIdentity(
    EntityProfile entityProfile, CurrencyUnit functionalCurrency, FiscalYearStart fiscalYearStart) {
  /** Validates one book identity. */
  public BookIdentity {
    Objects.requireNonNull(entityProfile, "entityProfile");
    Objects.requireNonNull(functionalCurrency, "functionalCurrency");
    Objects.requireNonNull(fiscalYearStart, "fiscalYearStart");
  }

  /** Returns the canonical display name of the accounting entity that owns this book. */
  public BookEntityName entityName() {
    return entityProfile.displayName();
  }
}
