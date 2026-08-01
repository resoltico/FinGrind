package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;

/** Commits one posting only as a child of an aggregate attested ledger plan. */
@FunctionalInterface
public interface PlanPostingCommitStore {
  /** Attempts one durable plan-child posting commit with a deferred attestation disposition. */
  PlanPostingCommitResult commitForPlan(
      PostingDraft postingDraft,
      PostingIdGenerator postingIdGenerator,
      AttestationPlanOperationAuthorizer attestationAuthorizer);
}
