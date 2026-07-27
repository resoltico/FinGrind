package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.PlanTaxRegistrationMutationOutcome;
import dev.erst.fingrind.executor.spi.PlanPostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Instant;

/** Shared ledger-plan transaction defaults for SQLite capability wrappers. */
interface SqlitePlanExecutionCapabilityView
    extends SqlitePlanExecutionSession, SqliteReadCapabilityView {
  /** Returns the account-registry owner for this plan-only session wrapper. */
  SqliteStoreAccountRegistryMutationOperations storeAccountRegistryMutationOperations();

  /** Returns the tax-administration owner for this plan-only session wrapper. */
  SqliteStoreAdministrationMutationOperations storeAdministrationMutationOperations();

  /** Returns the ordinary-posting owner for this plan-only session wrapper. */
  SqliteStorePostingMutationOperations storePostingMutationOperations();

  @Override
  default void beginLedgerPlanTransaction(
      String planId, AttestationPlanOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().transaction().beginAttestedPlan(planId, attestationAuthorizer);
  }

  @Override
  default void enterLedgerPlanStep(int stepOrder) {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().execution().enterPlanStep(stepOrder);
  }

  @Override
  default boolean hasCompletedLedgerPlanChildren() {
    storeThreadOwner().requireOwnerThread();
    return storeLifecycle().transactions().execution().hasCompletedPlanChildren();
  }

  @Override
  default void commitLedgerPlanTransaction() {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().transaction().commit();
  }

  @Override
  default void rollbackLedgerPlanTransaction() {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().transaction().rollback();
  }

  @Override
  default AttestationCommit appendPlanAttestation(
      Instant recordedAt, AttestationPlanOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    var transactions = storeLifecycle().transactions();
    var execution = transactions.execution();
    var planPreimages = execution.aggregatePreimages(attestationAuthorizer);
    var observedHead = execution.requireObservedAttestationHead();
    dev.erst.fingrind.core.attestation.AttestationVerification verification =
        SqliteAttestationEvidenceStore.appendPlanAuthorized(
            storeLifecycle().database(),
            observedHead,
            recordedAt,
            planPreimages,
            attestationAuthorizer);
    execution.markAggregateAppended(attestationAuthorizer);
    return dev.erst.fingrind.executor.AttestationCommitProjection.fromVerifiedAppend(
        new dev.erst.fingrind.core.attestation.AttestationAppendOutcome.Appended(verification));
  }

  @Override
  default PlanAccountDeclarationOutcome declareAccountForPlan(
      AccountDeclaration declaration,
      Instant declaredAt,
      AttestationPlanOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeAccountRegistryMutationOperations()
        .declareAccountForPlan(declaration, declaredAt, attestationAuthorizer);
  }

  @Override
  default PlanTaxRegistrationMutationOutcome declareTaxRegistrationForPlan(
      dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand command,
      Instant declaredAt,
      AttestationPlanOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeAdministrationMutationOperations()
        .declareTaxRegistrationForPlan(command, declaredAt, attestationAuthorizer);
  }

  @Override
  default PlanPostingCommitResult commitForPlan(
      PostingDraft postingDraft,
      PostingIdGenerator postingIdGenerator,
      AttestationPlanOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storePostingMutationOperations()
        .commitForPlan(postingDraft, postingIdGenerator, attestationAuthorizer);
  }
}
