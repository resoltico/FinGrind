package dev.erst.fingrind.contract.tax;

import java.util.Objects;

/** Closed result family for tax-registration declaration or update. */
public sealed interface DeclareTaxRegistrationResult
    permits DeclareTaxRegistrationResult.Declared,
        DeclareTaxRegistrationResult.Updated,
        DeclareTaxRegistrationResult.Unchanged,
        DeclareTaxRegistrationResult.Rejected {

  /** Success result carrying one first-declared durable tax-registration snapshot. */
  record Declared(DeclaredTaxRegistration registration) implements DeclareTaxRegistrationResult {
    public Declared {
      Objects.requireNonNull(registration, "registration");
    }
  }

  /** Success result carrying one updated durable tax-registration snapshot. */
  record Updated(DeclaredTaxRegistration registration) implements DeclareTaxRegistrationResult {
    public Updated {
      Objects.requireNonNull(registration, "registration");
    }
  }

  /** Success result carrying one unchanged durable tax-registration snapshot. */
  record Unchanged(DeclaredTaxRegistration registration) implements DeclareTaxRegistrationResult {
    public Unchanged {
      Objects.requireNonNull(registration, "registration");
    }
  }

  /** Deterministic refusal for declare-tax-registration. */
  record Rejected(TaxDeclarationRejection rejection) implements DeclareTaxRegistrationResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
