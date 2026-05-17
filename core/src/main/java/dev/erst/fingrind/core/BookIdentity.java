package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical identity metadata for one accounting entity book. */
public record BookIdentity(
    EntityProfile entityProfile,
    CurrencyUnit functionalCurrency,
    FiscalYearStart fiscalYearStart,
    AccountingBasis accountingBasis,
    TaxProfile taxProfile) {
  /** Validates one book identity. */
  public BookIdentity {
    Objects.requireNonNull(entityProfile, "entityProfile");
    Objects.requireNonNull(functionalCurrency, "functionalCurrency");
    Objects.requireNonNull(fiscalYearStart, "fiscalYearStart");
    Objects.requireNonNull(accountingBasis, "accountingBasis");
    Objects.requireNonNull(taxProfile, "taxProfile");
    if (entityProfile.taxRegistrationStatus() == TaxRegistrationStatus.REGISTERED
        && taxProfile.registrations().isEmpty()) {
      throw new IllegalArgumentException(
          "Registered tax status requires at least one declared tax registration.");
    }
    if (entityProfile.taxRegistrationStatus() != TaxRegistrationStatus.REGISTERED
        && (!taxProfile.registrations().isEmpty() || !taxProfile.taxCodeDefinitions().isEmpty())) {
      throw new IllegalArgumentException(
          "Declared tax registrations or tax codes require REGISTERED tax status.");
    }
  }

  /** Returns the canonical display name of the accounting entity that owns this book. */
  public BookEntityName entityName() {
    return entityProfile.displayName();
  }

  /** Returns the canonical entity form that drives policy selection for this book. */
  public EntityForm entityForm() {
    return entityProfile.entityForm();
  }
}
