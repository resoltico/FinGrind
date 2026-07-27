package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import java.util.Objects;

/** Tax-registration outcome available to one aggregate ledger-plan child step. */
public sealed interface PlanTaxRegistrationMutationOutcome
    permits PlanTaxRegistrationMutationOutcome.Declared,
        PlanTaxRegistrationMutationOutcome.Updated,
        PlanTaxRegistrationMutationOutcome.Unchanged,
        PlanTaxRegistrationMutationOutcome.Rejected {
  /** The plan declared a registration and deferred attestation to its aggregate operation. */
  record Declared(DeclaredTaxRegistration registration)
      implements PlanTaxRegistrationMutationOutcome {
    public Declared {
      Objects.requireNonNull(registration, "registration");
    }
  }

  /** The plan updated a registration and deferred attestation to its aggregate operation. */
  record Updated(DeclaredTaxRegistration registration)
      implements PlanTaxRegistrationMutationOutcome {
    public Updated {
      Objects.requireNonNull(registration, "registration");
    }
  }

  /** The requested registration already matched durable state, so the plan did not mutate it. */
  record Unchanged(DeclaredTaxRegistration registration)
      implements PlanTaxRegistrationMutationOutcome {
    public Unchanged {
      Objects.requireNonNull(registration, "registration");
    }
  }

  /** The registration declaration was rejected before mutation. */
  record Rejected(TaxDeclarationRejection rejection) implements PlanTaxRegistrationMutationOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
