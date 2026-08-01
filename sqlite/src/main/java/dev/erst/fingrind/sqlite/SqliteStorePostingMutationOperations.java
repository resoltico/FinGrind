package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationPostingEffectSnapshot;
import dev.erst.fingrind.core.attestation.AttestationPostingEvidenceDocument;
import dev.erst.fingrind.core.attestation.AttestationPostingLine;
import dev.erst.fingrind.core.attestation.AttestationPostingMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationPostingRequestSnapshot;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy.Decision;
import dev.erst.fingrind.executor.spi.PlanPostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.nio.file.Files;
import java.util.Objects;
import java.util.UUID;

/** Direct SQLite owner for ordinary and ledger-plan child posting mutations. */
final class SqliteStorePostingMutationOperations {
  private static final AttestationOperationKind POST_ENTRY_OPERATION =
      AttestationOperationKind.POST_ENTRY;

  /** Executes one action using the store's currently borrowed SQLite database. */
  @FunctionalInterface
  private interface BorrowedDatabaseAction<T> {
    /** Runs this action against the active SQLite database. */
    T run(SqliteNativeDatabase activeDatabase);
  }

  private final SqliteStoreContext context;
  private final SqliteStoreLifecycle lifecycle;
  private final PostingAcceptancePolicy postingAcceptancePolicy;
  private final SqliteAcceptedPostingPersistence postingPersistence;

  SqliteStorePostingMutationOperations(
      SqliteStoreContext context,
      SqliteStoreLifecycle lifecycle,
      SqliteCommitFaultHook commitFaultHook,
      PostingAcceptancePolicy postingAcceptancePolicy) {
    this.context = Objects.requireNonNull(context, "context");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    this.postingAcceptancePolicy =
        Objects.requireNonNull(postingAcceptancePolicy, "postingAcceptancePolicy");
    this.postingPersistence =
        new SqliteAcceptedPostingPersistence(
            Objects.requireNonNull(commitFaultHook, "commitFaultHook"));
  }

  PostingCommitResult commit(
      PostingDraft postingDraft,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    Objects.requireNonNull(postingDraft, "postingDraft");
    if (postingDraft.postingKind().isGenerated()) {
      throw new IllegalArgumentException(
          "Generated close postings must be committed through their reporting-period close workflow.");
    }
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    lifecycle.transactions().mutationAdmission().requireDirectMutationPermitted();
    if (Files.notExists(context.bookPath())) {
      return new PostingCommitResult.Rejected(new BookkeepingPostingRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          try {
            SqliteAttestedWriteAdmission admission =
                lifecycle
                    .transactions()
                    .mutationAdmission()
                    .admitDirectAttestedWrite(activeDatabase);
            transactionOwnership = admission.transactionOwnership();
            Decision decision =
                postingAcceptancePolicy.decisionFor(
                    postingDraft,
                    new SqliteTransactionValidationBook(activeDatabase, context.postingReader()));
            return switch (decision) {
              case Decision.Replay replay -> {
                SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
                yield new PostingCommitResult.Replayed(replay.postingFact());
              }
              case Decision.Rejected rejected -> {
                SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
                yield new PostingCommitResult.Rejected(rejected.rejection());
              }
              case Decision.Accepted accepted -> {
                PostingId postingId =
                    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator")
                        .nextPostingId();
                CommittedPosting postingFact =
                    accepted.acceptedPosting().materialize(postingId, postingDraft.provenance());
                postingPersistence.persistMaterializedPosting(
                    activeDatabase,
                    accepted.acceptedPosting(),
                    postingFact,
                    accepted.requestFingerprint());
                AttestationAppendOutcome.Appended attestationAppend =
                    SqliteAttestationEvidenceStore.appendAuthorized(
                            activeDatabase,
                            admission.observedHead(),
                            POST_ENTRY_OPERATION,
                            postingFact.provenance().recordedAt(),
                            AttestationPostingMutationProjection.project(
                                postingRequestSnapshot(postingDraft),
                                postingEffectSnapshot(postingFact)),
                            attestationAuthorizer)
                        .requireAppended();
                SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
                yield new PostingCommitResult.Appended(postingFact, attestationAppend);
              }
            };
          } catch (SqliteNativeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw SqliteStoreOperations.sqliteFailure(
                "Failed to commit SQLite posting fact.", exception);
          } catch (RuntimeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw exception;
          }
        });
  }

  PlanPostingCommitResult commitForPlan(
      PostingDraft postingDraft,
      PostingIdGenerator postingIdGenerator,
      dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer attestationAuthorizer) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    Objects.requireNonNull(postingDraft, "postingDraft");
    if (postingDraft.postingKind().isGenerated()) {
      throw new IllegalArgumentException(
          "Generated close postings must be committed through their reporting-period close workflow.");
    }
    Objects.requireNonNull(attestationAuthorizer, "attestationAuthorizer");
    lifecycle.transactions().mutationAdmission().requirePlanChildMutation(attestationAuthorizer);
    if (Files.notExists(context.bookPath())) {
      return new PlanPostingCommitResult.Rejected(
          new BookkeepingPostingRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          try {
            SqliteAttestedWriteAdmission admission =
                lifecycle
                    .transactions()
                    .mutationAdmission()
                    .admitPlanChildWrite(activeDatabase, attestationAuthorizer);
            transactionOwnership = admission.transactionOwnership();
            Decision decision =
                postingAcceptancePolicy.decisionFor(
                    postingDraft,
                    new SqliteTransactionValidationBook(activeDatabase, context.postingReader()));
            return switch (decision) {
              case Decision.Replay replay -> {
                SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
                yield new PlanPostingCommitResult.Replayed(replay.postingFact());
              }
              case Decision.Rejected rejected -> {
                SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
                yield new PlanPostingCommitResult.Rejected(rejected.rejection());
              }
              case Decision.Accepted accepted -> {
                PostingId postingId =
                    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator")
                        .nextPostingId();
                CommittedPosting postingFact =
                    accepted.acceptedPosting().materialize(postingId, postingDraft.provenance());
                var preimages =
                    AttestationPostingMutationProjection.project(
                        postingRequestSnapshot(postingDraft), postingEffectSnapshot(postingFact));
                postingPersistence.persistMaterializedPosting(
                    activeDatabase,
                    accepted.acceptedPosting(),
                    postingFact,
                    accepted.requestFingerprint());
                lifecycle
                    .transactions()
                    .mutationAdmission()
                    .recordCompletedPlanChild(
                        attestationAuthorizer, POST_ENTRY_OPERATION.wireToken(), preimages);
                SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
                yield new PlanPostingCommitResult.Deferred(postingFact);
              }
            };
          } catch (SqliteNativeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            IllegalStateException failure =
                SqliteStoreOperations.sqliteFailure(
                    "Failed to commit SQLite ledger-plan posting fact.", exception);
            lifecycle.transactions().mutationAdmission().abortAttestedPlanOnChildFailure(failure);
            throw failure;
          } catch (RuntimeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            lifecycle.transactions().mutationAdmission().abortAttestedPlanOnChildFailure(exception);
            throw exception;
          }
        });
  }

  private <T> T withBorrowedDatabase(BorrowedDatabaseAction<T> action) {
    return action.run(lifecycle.database());
  }

  private static AttestationPostingRequestSnapshot postingRequestSnapshot(
      PostingDraft postingDraft) {
    return new AttestationPostingRequestSnapshot(
        POST_ENTRY_OPERATION.wireToken(),
        postingDraft.provenance().requestProvenance().idempotencyKey().value(),
        postingDraft.provenance().requestProvenance().causationId().value(),
        postingDraft.provenance().sourceChannel().wireValue(),
        postingDraft.journalEntry().effectiveDate(),
        postingDraft.postingKind().wireValue(),
        postingDraft
            .postingLineage()
            .reversalReference()
            .map(reference -> reference.priorPostingId().value())
            .orElse(null),
        postingDraft.postingLineage().reversalReason().map(reason -> reason.value()).orElse(null),
        postingDraft.evidence().sourceDocuments().stream()
            .map(
                document ->
                    new AttestationPostingEvidenceDocument(
                        document.sourceDocumentId().value(),
                        document.sourceDocumentType().value(),
                        document.documentDate()))
            .toList(),
        postingDraft.journalEntry().lines().stream()
            .map(
                line ->
                    new AttestationPostingLine(
                        line.accountCode().value(),
                        line.side().wireValue(),
                        line.amount().currencyUnit().code(),
                        line.amount().minorUnits()))
            .toList());
  }

  private static AttestationPostingEffectSnapshot postingEffectSnapshot(CommittedPosting posting) {
    return new AttestationPostingEffectSnapshot(
        UUID.fromString(posting.postingId().value()),
        POST_ENTRY_OPERATION.wireToken(),
        posting.postingKind().wireValue(),
        posting.postingOriginKind().wireValue(),
        posting.provenance().recordedAt(),
        posting
            .postingLineage()
            .reversalReference()
            .map(reference -> UUID.fromString(reference.priorPostingId().value()))
            .orElse(null),
        UUID.fromString(posting.provenance().requestProvenance().commandId().value()));
  }
}
