package dev.erst.fingrind.core;

import java.time.LocalDate;
import java.util.Objects;

/** Canonical identity metadata for one accounting entity book. */
public record BookIdentity(
    EntityProfile entityProfile,
    BookDoctrine bookDoctrine,
    CurrencyUnit functionalCurrency,
    FiscalYearStart fiscalYearStart,
    LocalDate bookStartEffectiveDate) {
  /** Validates one book identity. */
  public BookIdentity {
    Objects.requireNonNull(entityProfile, "entityProfile");
    Objects.requireNonNull(bookDoctrine, "bookDoctrine");
    Objects.requireNonNull(functionalCurrency, "functionalCurrency");
    Objects.requireNonNull(fiscalYearStart, "fiscalYearStart");
    Objects.requireNonNull(bookStartEffectiveDate, "bookStartEffectiveDate");
  }

  /** Returns the canonical display name of the accounting entity that owns this book. */
  public BookEntityName entityName() {
    return entityProfile.displayName();
  }
}
