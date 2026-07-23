package dev.erst.fingrind.contract.tax;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Closed result family for tax-registration declaration or update. */
public sealed interface DeclareTaxRegistrationResult
    permits DeclareTaxRegistrationResult.Declared,
        DeclareTaxRegistrationResult.Updated,
        DeclareTaxRegistrationResult.Unchanged,
        DeclareTaxRegistrationResult.Rejected {

  /** Success result carrying one first-declared durable tax-registration snapshot. */
  record Declared(DeclaredTaxRegistration registration, AttestationCommit attestationCommit)
      implements DeclareTaxRegistrationResult {
    public Declared {
      Objects.requireNonNull(registration, "registration");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
    }
  }

  /** Success result carrying one updated durable tax-registration snapshot. */
  record Updated(DeclaredTaxRegistration registration, AttestationCommit attestationCommit)
      implements DeclareTaxRegistrationResult {
    public Updated {
      Objects.requireNonNull(registration, "registration");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
    }
  }

  /** Success result carrying one unchanged durable tax-registration snapshot. */
  record Unchanged(
      DeclaredTaxRegistration registration, @Nullable AttestationCommit attestationCommit)
      implements DeclareTaxRegistrationResult {
    public Unchanged {
      Objects.requireNonNull(registration, "registration");
      if (attestationCommit != null) {
        throw new IllegalArgumentException(
            "An unchanged tax registration must not report a newly appended attestation operation.");
      }
    }
  }

  /** Deterministic refusal for declare-tax-registration. */
  record Rejected(TaxDeclarationRejection rejection) implements DeclareTaxRegistrationResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
