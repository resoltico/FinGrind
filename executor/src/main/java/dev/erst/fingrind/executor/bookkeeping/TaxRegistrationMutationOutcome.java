package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import java.util.Objects;

/** Local outcome of one durable tax-registration mutation before public response projection. */
public sealed interface TaxRegistrationMutationOutcome
    permits TaxRegistrationMutationOutcome.Declared,
        TaxRegistrationMutationOutcome.Updated,
        TaxRegistrationMutationOutcome.Unchanged,
        TaxRegistrationMutationOutcome.Rejected {
  /** First durable declaration with its exact newly appended attestation verification. */
  record Declared(
      DeclaredTaxRegistration registration, AttestationAppendOutcome.Appended attestationAppend)
      implements TaxRegistrationMutationOutcome {
    public Declared {
      Objects.requireNonNull(registration, "registration");
      Objects.requireNonNull(attestationAppend, "attestationAppend");
    }
  }

  /** Durable replacement with its exact newly appended attestation verification. */
  record Updated(
      DeclaredTaxRegistration registration, AttestationAppendOutcome.Appended attestationAppend)
      implements TaxRegistrationMutationOutcome {
    public Updated {
      Objects.requireNonNull(registration, "registration");
      Objects.requireNonNull(attestationAppend, "attestationAppend");
    }
  }

  /** No-op declaration that appended no operation. */
  record Unchanged(DeclaredTaxRegistration registration) implements TaxRegistrationMutationOutcome {
    public Unchanged {
      Objects.requireNonNull(registration, "registration");
    }
  }

  /** Deterministic refusal before a tax-registration mutation is persisted. */
  record Rejected(TaxDeclarationRejection rejection) implements TaxRegistrationMutationOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
