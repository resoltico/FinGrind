package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome;
import java.time.Instant;

/** Writes one account declaration as a child of an aggregate attested ledger plan. */
@FunctionalInterface
public interface PlanAccountDeclarationStore {
  /** Persists one declared-account child mutation and defers its attestation to the plan. */
  PlanAccountDeclarationOutcome declareAccountForPlan(
      AccountDeclaration declaration,
      Instant declaredAt,
      AttestationPlanOperationAuthorizer attestationAuthorizer);
}
