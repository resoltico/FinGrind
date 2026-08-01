package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.attestation.AttestationAccountMutationIntent;
import dev.erst.fingrind.core.attestation.AttestationAccountMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationEffectMutation;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationPostingMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationTaxRegistrationMutationProjection;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationDecision;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.PlanTaxRegistrationMutationOutcome;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.spi.LedgerPlanExecutionStore;
import dev.erst.fingrind.executor.spi.LedgerPlanReadOnlyExecutionStore;
import dev.erst.fingrind.executor.spi.PlanPostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** In-memory composite session used by executor tests and non-durable harness composition. */
public final class InMemoryBookSession extends AbstractInMemoryBookReadSession
    implements LedgerPlanExecutionStore, LedgerPlanReadOnlyExecutionStore, AutoCloseable {
  private final InMemoryLedgerPlanState ledgerPlanState = new InMemoryLedgerPlanState();

  /** Initializes this in-memory fixture without representing a protected-book production write. */
  @Override
  public BookOpeningOutcome openBook(
      Instant initializedAt, BookIdentity bookIdentity, List<AccountDeclaration> seededAccounts) {
    return super.openBook(initializedAt, bookIdentity, seededAccounts);
  }

  @Override
  public void close() {
    // No resources to release for the in-memory test fixture.
  }

  @Override
  public void beginLedgerPlanTransaction(
      String planId, AttestationPlanOperationAuthorizer attestationAuthorizer) {
    InMemoryBookSessionSupport.withLock(
        lock, () -> ledgerPlanState.begin(planId, attestationAuthorizer, false, snapshotState()));
  }

  @Override
  public void beginReadOnlyLedgerPlanTransaction(String planId) {
    InMemoryBookSessionSupport.withLock(
        lock, () -> ledgerPlanState.begin(planId, null, true, snapshotState()));
  }

  @Override
  public void enterLedgerPlanStep(int stepOrder) {
    InMemoryBookSessionSupport.withLock(lock, () -> ledgerPlanState.enterStep(stepOrder));
  }

  @Override
  public boolean hasCompletedLedgerPlanChildren() {
    return InMemoryBookSessionSupport.withLock(lock, ledgerPlanState::hasCompletedChildren);
  }

  @Override
  public AttestationCommit appendPlanAttestation(
      Instant recordedAt, AttestationPlanOperationAuthorizer attestationAuthorizer) {
    return InMemoryBookSessionSupport.withLock(
        lock, () -> ledgerPlanState.appendAggregate(recordedAt, attestationAuthorizer));
  }

  @Override
  public PlanAccountDeclarationOutcome declareAccountForPlan(
      AccountDeclaration declaration,
      Instant declaredAt,
      AttestationPlanOperationAuthorizer attestationAuthorizer) {
    Objects.requireNonNull(declaration, "declaration");
    Objects.requireNonNull(declaredAt, "declaredAt");
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          ledgerPlanState.requireChildMutation(attestationAuthorizer);
          try {
            if (!initialized) {
              return new PlanAccountDeclarationOutcome.Rejected(
                  new BookkeepingAdministrationRejection.BookNotInitialized());
            }
            AccountDeclarationDecision decision =
                dev.erst.fingrind.executor.bookkeeping.RegisteredAccount.declare(
                    accountsByCode.get(declaration.accountCode()), declaration, declaredAt);
            return switch (decision) {
              case AccountDeclarationDecision.Declared declared -> {
                accountsByCode.put(declaration.accountCode(), declared.account());
                ledgerPlanState.recordCompletedChild(
                    AttestationOperationKind.DECLARE_ACCOUNT.wireToken(),
                    AttestationAccountMutationProjection.project(
                        AttestationAccountMutationIntent.DECLARATION,
                        AttestationOperationKind.DECLARE_ACCOUNT.wireToken(),
                        InMemoryBookAttestationFixtureProjections.requestedSnapshot(declaration),
                        InMemoryBookAttestationFixtureProjections.snapshot(declared.account()),
                        InMemoryBookAttestationFixtureProjections.declarationMutation(decision)),
                    null);
                yield new PlanAccountDeclarationOutcome.Declared(declared.account());
              }
              case AccountDeclarationDecision.Reactivated reactivated -> {
                accountsByCode.put(declaration.accountCode(), reactivated.account());
                ledgerPlanState.recordCompletedChild(
                    AttestationOperationKind.DECLARE_ACCOUNT.wireToken(),
                    AttestationAccountMutationProjection.project(
                        AttestationAccountMutationIntent.DECLARATION,
                        AttestationOperationKind.DECLARE_ACCOUNT.wireToken(),
                        InMemoryBookAttestationFixtureProjections.requestedSnapshot(declaration),
                        InMemoryBookAttestationFixtureProjections.snapshot(reactivated.account()),
                        InMemoryBookAttestationFixtureProjections.declarationMutation(decision)),
                    null);
                yield new PlanAccountDeclarationOutcome.Reactivated(reactivated.account());
              }
              case AccountDeclarationDecision.Renamed renamed -> {
                accountsByCode.put(declaration.accountCode(), renamed.account());
                ledgerPlanState.recordCompletedChild(
                    AttestationOperationKind.DECLARE_ACCOUNT.wireToken(),
                    AttestationAccountMutationProjection.project(
                        AttestationAccountMutationIntent.DECLARATION,
                        AttestationOperationKind.DECLARE_ACCOUNT.wireToken(),
                        InMemoryBookAttestationFixtureProjections.requestedSnapshot(declaration),
                        InMemoryBookAttestationFixtureProjections.snapshot(renamed.account()),
                        InMemoryBookAttestationFixtureProjections.declarationMutation(decision)),
                    null);
                yield new PlanAccountDeclarationOutcome.Renamed(renamed.account());
              }
              case AccountDeclarationDecision.Unchanged unchanged ->
                  new PlanAccountDeclarationOutcome.Unchanged(unchanged.account());
              case AccountDeclarationDecision.Rejected rejected ->
                  new PlanAccountDeclarationOutcome.Rejected(rejected.rejection());
            };
          } catch (RuntimeException exception) {
            ledgerPlanState.rollback(this::restoreSnapshot);
            throw exception;
          }
        });
  }

  @Override
  public PlanTaxRegistrationMutationOutcome declareTaxRegistrationForPlan(
      DeclareTaxRegistrationCommand command,
      Instant declaredAt,
      AttestationPlanOperationAuthorizer attestationAuthorizer) {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(declaredAt, "declaredAt");
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          ledgerPlanState.requireChildMutation(attestationAuthorizer);
          try {
            if (!initialized) {
              return new PlanTaxRegistrationMutationOutcome.Rejected(
                  new TaxDeclarationRejection.BookNotInitialized());
            }
            DeclaredTaxRegistration existing =
                taxRegistrationsById.get(command.taxRegistrationId());
            DeclaredTaxRegistration candidate =
                new DeclaredTaxRegistration(
                    command.taxRegistrationId(),
                    command.taxRegistrationName(),
                    command.jurisdiction(),
                    command.registrationNumber(),
                    command.payableAccountCode(),
                    command.recoverableAccountCode(),
                    command.obligationFrequency(),
                    command.dueDaysAfterPeriodEnd(),
                    command.taxCodes(),
                    existing == null ? declaredAt : existing.declaredAt());
            if (existing != null && existing.equals(candidate)) {
              return new PlanTaxRegistrationMutationOutcome.Unchanged(existing);
            }
            taxRegistrationsById.put(candidate.taxRegistrationId(), candidate);
            ledgerPlanState.recordCompletedChild(
                AttestationOperationKind.DECLARE_TAX_REGISTRATION.wireToken(),
                AttestationTaxRegistrationMutationProjection.project(
                    AttestationOperationKind.DECLARE_TAX_REGISTRATION.wireToken(),
                    InMemoryPostingAttestationFixtureProjections.taxRegistrationSnapshot(command),
                    InMemoryPostingAttestationFixtureProjections.taxRegistrationSnapshot(candidate),
                    existing == null
                        ? AttestationEffectMutation.CREATE
                        : AttestationEffectMutation.AMEND),
                null);
            return existing == null
                ? new PlanTaxRegistrationMutationOutcome.Declared(candidate)
                : new PlanTaxRegistrationMutationOutcome.Updated(candidate);
          } catch (RuntimeException exception) {
            ledgerPlanState.rollback(this::restoreSnapshot);
            throw exception;
          }
        });
  }

  @Override
  public PlanPostingCommitResult commitForPlan(
      PostingDraft postingDraft,
      PostingIdGenerator postingIdGenerator,
      AttestationPlanOperationAuthorizer attestationAuthorizer) {
    Objects.requireNonNull(postingDraft, "postingDraft");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          ledgerPlanState.requireChildMutation(attestationAuthorizer);
          try {
            return switch (postingAcceptancePolicy.decisionFor(postingDraft, this)) {
              case PostingAcceptancePolicy.Decision.Replay replay ->
                  new PlanPostingCommitResult.Replayed(replay.postingFact());
              case PostingAcceptancePolicy.Decision.Rejected rejected ->
                  new PlanPostingCommitResult.Rejected(rejected.rejection());
              case PostingAcceptancePolicy.Decision.Accepted accepted -> {
                CommittedPosting postingFact =
                    accepted
                        .acceptedPosting()
                        .materialize(postingIdGenerator.nextPostingId(), postingDraft.provenance());
                var reversalReference = postingFact.postingLineage().reversalReference();
                if (reversalReference.isPresent()
                    && reversalsByPriorPostingId.containsKey(
                        reversalReference.orElseThrow().priorPostingId())) {
                  yield new PlanPostingCommitResult.Rejected(
                      new BookkeepingPostingRejection.ReversalAlreadyExists(
                          reversalReference.orElseThrow().priorPostingId()));
                }
                IdempotencyKey idempotencyKey =
                    postingFact.provenance().requestProvenance().idempotencyKey();
                postingsByIdempotencyKey.put(
                    idempotencyKey,
                    new StoredRequestPosting(postingFact, accepted.requestFingerprint()));
                postingsByPostingId.put(postingFact.postingId(), postingFact);
                if (reversalReference.isPresent()) {
                  var postedReversal = reversalReference.orElseThrow();
                  reversalsByPriorPostingId.put(postedReversal.priorPostingId(), postingFact);
                }
                inventoryMovementsByPostingId.put(
                    postingFact.postingId(), accepted.acceptedPosting().inventoryMovements());
                inventoryStateByAccount.putAll(
                    accepted.acceptedPosting().resultingInventoryStates());
                ledgerPlanState.recordCompletedChild(
                    AttestationOperationKind.POST_ENTRY.wireToken(),
                    AttestationPostingMutationProjection.project(
                        InMemoryPostingAttestationFixtureProjections.postingRequestSnapshot(
                            postingDraft),
                        InMemoryPostingAttestationFixtureProjections.postingEffectSnapshot(
                            postingFact)),
                    postingFact.postingId());
                yield new PlanPostingCommitResult.Deferred(postingFact);
              }
            };
          } catch (RuntimeException exception) {
            ledgerPlanState.rollback(this::restoreSnapshot);
            throw exception;
          }
        });
  }

  @Override
  public Map<PostingId, AttestationCommit> attestationCommitsFor(Set<PostingId> postingIds) {
    return InMemoryBookSessionSupport.withLock(
        lock, () -> ledgerPlanState.commitmentsFor(postingIds));
  }

  @Override
  public void commitLedgerPlanTransaction() {
    InMemoryBookSessionSupport.withLock(lock, () -> ledgerPlanState.commit(this::restoreSnapshot));
  }

  @Override
  public void rollbackLedgerPlanTransaction() {
    InMemoryBookSessionSupport.withLock(
        lock, () -> ledgerPlanState.rollback(this::restoreSnapshot));
  }

  @Override
  protected void requireDirectMutationPermitted() {
    ledgerPlanState.requireDirectMutationPermitted();
  }
}
