package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.PlanTaxRegistrationMutationOutcome;
import java.time.Instant;

/** Writes one tax-registration mutation as a child of an aggregate attested ledger plan. */
@FunctionalInterface
public interface PlanTaxRegistrationStore {
  /** Persists one tax-registration child mutation and defers its attestation to the plan. */
  PlanTaxRegistrationMutationOutcome declareTaxRegistrationForPlan(
      DeclareTaxRegistrationCommand command,
      Instant declaredAt,
      AttestationPlanOperationAuthorizer attestationAuthorizer);
}
