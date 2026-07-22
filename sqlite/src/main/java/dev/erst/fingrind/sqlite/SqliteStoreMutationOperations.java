package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationPostingEffectSnapshot;
import dev.erst.fingrind.core.attestation.AttestationPostingEvidenceDocument;
import dev.erst.fingrind.core.attestation.AttestationPostingLine;
import dev.erst.fingrind.core.attestation.AttestationPostingMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationPostingRequestSnapshot;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy.Decision;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Mutation operations over one SQLite-backed book session. */
final class SqliteStoreMutationOperations {
  private static final AttestationOperationKind POST_ENTRY_OPERATION =
      AttestationOperationKind.POST_ENTRY;

  /** One mutation callback that borrows the session-owned SQLite handle without closing it. */
  @FunctionalInterface
  private interface BorrowedDatabaseAction<T> {
    /** Runs one mutation callback against the active SQLite handle. */
    T run(SqliteNativeDatabase activeDatabase);
  }

  private final SqliteStoreContext context;
  private final SqliteStoreLifecycle lifecycle;
  private final PostingAcceptancePolicy postingAcceptancePolicy;
  private final SqliteStoreAdministrationMutationOperations administrationOperations;
  private final SqliteStoreAccountRegistryMutationOperations accountRegistryOperations;
  private final SqliteClosingMutationOperations closingOperations;

  SqliteStoreMutationOperations(
      SqliteStoreContext context,
      SqliteStoreLifecycle lifecycle,
      SqliteCommitFaultHook commitFaultHook,
      PostingAcceptancePolicy postingAcceptancePolicy) {
    this.context = Objects.requireNonNull(context, "context");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    this.postingAcceptancePolicy =
        Objects.requireNonNull(postingAcceptancePolicy, "postingAcceptancePolicy");
    this.administrationOperations =
        new SqliteStoreAdministrationMutationOperations(context, lifecycle);
    this.accountRegistryOperations =
        new SqliteStoreAccountRegistryMutationOperations(context, lifecycle);
    this.closingOperations =
        new SqliteClosingMutationOperations(
            context,
            lifecycle,
            Objects.requireNonNull(commitFaultHook, "commitFaultHook"),
            this.postingAcceptancePolicy);
  }

  dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome openAttestedBook(
      Instant initializedAt,
      BookIdentity bookIdentity,
      List<dev.erst.fingrind.executor.bookkeeping.AccountDeclaration> seededAccounts,
      AttestationEvidence genesisEvidence) {
    return administrationOperations.openAttestedBook(
        initializedAt, bookIdentity, seededAccounts, genesisEvidence);
  }

  dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome declareAccount(
      dev.erst.fingrind.executor.bookkeeping.AccountDeclaration declaration,
      Instant declaredAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    return accountRegistryOperations.declareAccount(declaration, declaredAt, attestationAuthorizer);
  }

  dev.erst.fingrind.executor.bookkeeping.AccountAmendmentOutcome amendAccount(
      dev.erst.fingrind.executor.bookkeeping.AccountDeclaration amendment,
      Instant amendedAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    return accountRegistryOperations.amendAccount(amendment, amendedAt, attestationAuthorizer);
  }

  dev.erst.fingrind.executor.bookkeeping.AccountRetirementOutcome retireAccount(
      dev.erst.fingrind.core.AccountCode accountCode,
      Instant retiredAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    return accountRegistryOperations.retireAccount(accountCode, retiredAt, attestationAuthorizer);
  }

  DeclareTaxRegistrationResult declareTaxRegistration(
      DeclareTaxRegistrationCommand command,
      Instant declaredAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    return administrationOperations.declareTaxRegistration(
        command, declaredAt, attestationAuthorizer);
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
    if (Files.notExists(context.bookPath())) {
      return new PostingCommitResult.Rejected(new BookkeepingPostingRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          try {
            SqliteAttestedWriteAdmission admission =
                lifecycle.transactions().admitAttestedWrite(activeDatabase);
            transactionOwnership = admission.transactionOwnership();
            Decision decision =
                postingAcceptancePolicy.decisionFor(
                    postingDraft,
                    new SqliteTransactionValidationBook(activeDatabase, context.postingReader()));
            return switch (decision) {
              case Decision.Replay replay -> {
                SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
                yield new PostingCommitResult.Committed(replay.postingFact(), true, null);
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
                dev.erst.fingrind.core.attestation.AttestationVerification attestationVerification =
                    SqliteAttestationEvidenceStore.appendAuthorized(
                        activeDatabase,
                        admission.observedHead(),
                        POST_ENTRY_OPERATION,
                        postingFact.provenance().recordedAt(),
                        AttestationPostingMutationProjection.project(
                            postingRequestSnapshot(postingDraft),
                            postingEffectSnapshot(postingFact)),
                        attestationAuthorizer);
                closingOperations.persistMaterializedPosting(
                    activeDatabase,
                    accepted.acceptedPosting(),
                    accepted.requestFingerprint(),
                    postingFact);
                SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
                yield new PostingCommitResult.Committed(
                    postingFact, false, attestationVerification);
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

  InterimResultSweepOutcome interimResultSweep(
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    return closingOperations.interimResultSweep(
        reportingPeriod,
        bookIdentity,
        planner,
        currentUtcDate,
        sweptAt,
        postingIdGenerator,
        attestationAuthorizer);
  }

  InterimResultSweepOutcome interimResultSweep(
      LocalDate throughEffectiveDate,
      LocalDate bookStartDate,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    return closingOperations.interimResultSweep(
        throughEffectiveDate,
        bookStartDate,
        bookIdentity,
        planner,
        currentUtcDate,
        sweptAt,
        postingIdGenerator,
        attestationAuthorizer);
  }

  InterimResultSweepOutcome interimResultSweep(
      dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft interimResultSweepDraft,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    return closingOperations.interimResultSweep(
        interimResultSweepDraft, postingIdGenerator, attestationAuthorizer);
  }

  FiscalYearCloseOutcome fiscalYearClose(
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      FiscalYearClosePlanner planner,
      LocalDate currentUtcDate,
      Instant closedAt,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    return closingOperations.fiscalYearClose(
        reportingPeriod,
        bookIdentity,
        planner,
        currentUtcDate,
        closedAt,
        postingIdGenerator,
        attestationAuthorizer);
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
