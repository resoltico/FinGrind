package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationGenesis;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.AttestationCommitProjection;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.PlanTaxRegistrationMutationOutcome;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TaxRegistrationMutationOutcome;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Administrative mutation operations over one SQLite-backed book session. */
final class SqliteStoreAdministrationMutationOperations {
  private static final AttestationOperationKind DECLARE_TAX_REGISTRATION_OPERATION =
      AttestationOperationKind.DECLARE_TAX_REGISTRATION;

  /** One mutation callback that borrows the session-owned SQLite handle without closing it. */
  @FunctionalInterface
  private interface BorrowedDatabaseAction<T> {
    /** Runs one mutation callback against the active SQLite handle. */
    T run(SqliteNativeDatabase activeDatabase);
  }

  /**
   * Builds the complete returned opening outcome while the initialization transaction is active.
   */
  @FunctionalInterface
  interface OpenedBookOutcomeFactory {
    /** Projects one verified genesis append into the complete bookkeeping opening outcome. */
    BookOpeningOutcome.Opened create(
        Instant initializedAt,
        BookIdentity bookIdentity,
        AttestationEvidence genesisEvidence,
        AttestationVerification verification);
  }

  /** Commits one initialized-book transaction after its complete response facts are prebuilt. */
  @FunctionalInterface
  interface OpenBookCommitter {
    /** Commits the owned initialization transaction. */
    void commit(
        SqliteNativeDatabase activeDatabase, SqliteTransactionOwnership transactionOwnership);
  }

  private final SqliteStoreContext context;
  private final SqliteStoreLifecycle lifecycle;
  private final OpenedBookOutcomeFactory openedBookOutcomeFactory;
  private final OpenBookCommitter openBookCommitter;

  SqliteStoreAdministrationMutationOperations(
      SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    this(
        context,
        lifecycle,
        SqliteStoreAdministrationMutationOperations::openedBookOutcome,
        SqliteStoreOperations::commitIfOwned);
  }

  SqliteStoreAdministrationMutationOperations(
      SqliteStoreContext context,
      SqliteStoreLifecycle lifecycle,
      OpenedBookOutcomeFactory openedBookOutcomeFactory) {
    this(context, lifecycle, openedBookOutcomeFactory, SqliteStoreOperations::commitIfOwned);
  }

  SqliteStoreAdministrationMutationOperations(
      SqliteStoreContext context,
      SqliteStoreLifecycle lifecycle,
      OpenBookCommitter openBookCommitter) {
    this(
        context,
        lifecycle,
        SqliteStoreAdministrationMutationOperations::openedBookOutcome,
        openBookCommitter);
  }

  SqliteStoreAdministrationMutationOperations(
      SqliteStoreContext context,
      SqliteStoreLifecycle lifecycle,
      OpenedBookOutcomeFactory openedBookOutcomeFactory,
      OpenBookCommitter openBookCommitter) {
    this.context = Objects.requireNonNull(context, "context");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    this.openedBookOutcomeFactory =
        Objects.requireNonNull(openedBookOutcomeFactory, "openedBookOutcomeFactory");
    this.openBookCommitter = Objects.requireNonNull(openBookCommitter, "openBookCommitter");
  }

  /**
   * Initializes one blank protected book with self-authorizing genesis evidence.
   *
   * <p>If initialization fails, the lifecycle deliberately retains any provisional caller-path
   * artifact. The failure is not an initialized-book outcome and this method never compensates by
   * unlinking a path that may have been replaced after SQLite created it.
   */
  BookOpeningOutcome openAttestedBook(
      Instant initializedAt,
      BookIdentity bookIdentity,
      List<AccountDeclaration> seededAccounts,
      AttestationEvidence genesisEvidence) {
    AttestationEvidence checkedGenesisEvidence =
        Objects.requireNonNull(genesisEvidence, "genesisEvidence");
    AttestationGenesis.requireMatchingBookIdentity(checkedGenesisEvidence, bookIdentity);
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableInitialization();
    lifecycle.transactions().mutationAdmission().requireDirectMutationPermitted();
    SqliteBookSchemaBootstrap.ensureParentDirectory(context.bookPath());
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          try {
            SqliteBookStateSnapshot snapshot = lifecycle.stateSnapshot(activeDatabase);
            Optional<BookOpeningOutcome> preexistingOutcome =
                snapshot.state().openBookResult(snapshot.userVersion());
            if (preexistingOutcome.isPresent()) {
              return preexistingOutcome.orElseThrow();
            }

            transactionOwnership =
                lifecycle.transactions().transaction().beginImmediateIfNeeded(activeDatabase);
            SqliteBookSchemaBootstrap.initializeBook(activeDatabase);
            SqliteBookIntegrityVerifier.recordSchemaFingerprint(activeDatabase);
            SqliteMutationWriter.insertInitializedAt(activeDatabase, initializedAt);
            SqliteMutationWriter.insertBookIdentity(activeDatabase, bookIdentity);
            for (AccountDeclaration seededAccount : seededAccounts) {
              RegisteredAccount declaredAccount =
                  RegisteredAccount.declareNew(seededAccount, initializedAt);
              SqliteAccountRegistryMutationWriter.upsertAccount(activeDatabase, declaredAccount);
            }
            AttestationVerification verification =
                SqliteAttestationEvidenceStore.append(
                    activeDatabase, new byte[32], checkedGenesisEvidence);
            SqliteAuditEventWriter.insertAuditEvent(
                activeDatabase, BookAuditEvent.bookOpened(initializedAt));
            SqliteBookStateSnapshot initializedSnapshot =
                new SqliteBookStateSnapshot(
                    SqliteBookContract.APPLICATION_ID,
                    SqliteBookContract.FORMAT_VERSION,
                    SqliteBookState.INITIALIZED_FINGRIND);
            BookOpeningOutcome.Opened openedBook =
                openedBookOutcomeFactory.create(
                    initializedAt, bookIdentity, checkedGenesisEvidence, verification);
            completeOpenedBookCommit(activeDatabase, transactionOwnership, openedBook);
            lifecycle.cacheState(initializedSnapshot);
            return openedBook;
          } catch (RollbackSafeOpenBookFailure failure) {
            RuntimeException originalFailure = failure.originalFailure();
            originalFailure.addSuppressed(failure);
            if (originalFailure instanceof SqliteNativeException nativeFailure) {
              throw SqliteStoreOperations.sqliteFailure(
                  "Failed to initialize SQLite book.", nativeFailure);
            }
            throw originalFailure;
          } catch (SqliteOpenBookCompletionUncertainException exception) {
            throw exception;
          } catch (SqliteNativeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw SqliteStoreOperations.sqliteFailure(
                "Failed to initialize SQLite book.", exception);
          } catch (RuntimeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw exception;
          }
        });
  }

  /** Completes initialization commit without allowing a proved rollback to be rolled back twice. */
  private void completeOpenedBookCommit(
      SqliteNativeDatabase activeDatabase,
      SqliteTransactionOwnership transactionOwnership,
      BookOpeningOutcome.Opened openedBook) {
    Optional<RuntimeException> rollbackSafeCommitFailure =
        commitOpenedBook(activeDatabase, transactionOwnership, openedBook);
    if (rollbackSafeCommitFailure.isPresent()) {
      throw new RollbackSafeOpenBookFailure(rollbackSafeCommitFailure.orElseThrow());
    }
  }

  /**
   * Commits the prepared initialization and classifies an unacknowledged COMMIT from fresh state.
   *
   * <p>SQLite can durably apply a COMMIT before the caller observes its acknowledgement. The
   * rollback attempt is therefore followed by an uncached audited snapshot: only an observed blank
   * SQLite file proves that founder-key rollback remains safe. Every other state, including an
   * unreadable state, retains the prebuilt opening facts for reconciliation. A returned failure has
   * already been rolled back; its caller must not attempt a second transaction cleanup.
   */
  private Optional<RuntimeException> commitOpenedBook(
      SqliteNativeDatabase activeDatabase,
      SqliteTransactionOwnership transactionOwnership,
      BookOpeningOutcome.Opened openedBook) {
    try {
      openBookCommitter.commit(activeDatabase, transactionOwnership);
      return Optional.empty();
    } catch (RuntimeException commitFailure) {
      rollbackAfterUnacknowledgedCommit(activeDatabase, transactionOwnership, commitFailure);
      lifecycle.clearCachedState();
      if (commitFailureLeftProvablyBlankBook(activeDatabase, commitFailure)) {
        return Optional.of(commitFailure);
      }
      throw new SqliteOpenBookCompletionUncertainException(openedBook, commitFailure);
    }
  }

  /**
   * Signals that commit classification already restored blank state and released transaction
   * custody.
   */
  private static final class RollbackSafeOpenBookFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final RuntimeException originalFailure;

    private RollbackSafeOpenBookFailure(RuntimeException originalFailure) {
      super(Objects.requireNonNull(originalFailure, "originalFailure"));
      this.originalFailure = Objects.requireNonNull(originalFailure, "originalFailure");
    }

    private RuntimeException originalFailure() {
      return originalFailure;
    }
  }

  /**
   * Attempts rollback without mistaking a post-COMMIT "no active transaction" response for a second
   * user-visible failure.
   *
   * <p>The subsequent fresh read decides custody. A rollback failure remains attached to the
   * original COMMIT failure as diagnostic evidence rather than being logged as if it were an
   * independent cleanup defect.
   */
  private static void rollbackAfterUnacknowledgedCommit(
      SqliteNativeDatabase activeDatabase,
      SqliteTransactionOwnership transactionOwnership,
      RuntimeException commitFailure) {
    if (transactionOwnership != SqliteTransactionOwnership.OWNED) {
      return;
    }
    try {
      activeDatabase.executeStatement("rollback");
    } catch (SqliteNativeException | IllegalStateException rollbackFailure) {
      commitFailure.addSuppressed(rollbackFailure);
    }
  }

  /** Returns whether a fresh audited observation proves that the failed COMMIT left no book. */
  private boolean commitFailureLeftProvablyBlankBook(
      SqliteNativeDatabase activeDatabase, RuntimeException commitFailure) {
    try {
      return context.bookStateReader().snapshot(activeDatabase).state()
          == SqliteBookState.BLANK_SQLITE;
    } catch (RuntimeException inspectionFailure) {
      commitFailure.addSuppressed(inspectionFailure);
      return false;
    }
  }

  private static BookOpeningOutcome.Opened openedBookOutcome(
      Instant initializedAt,
      BookIdentity bookIdentity,
      AttestationEvidence genesisEvidence,
      AttestationVerification verification) {
    return new BookOpeningOutcome.Opened(
        initializedAt,
        bookIdentity,
        AttestationVerifier.verifyAndInspectBook(List.of(genesisEvidence)).registry(),
        AttestationCommitProjection.fromVerifiedAppend(
            new AttestationAppendOutcome.Appended(verification)));
  }

  TaxRegistrationMutationOutcome declareTaxRegistration(
      DeclareTaxRegistrationCommand command,
      Instant declaredAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(declaredAt, "declaredAt");
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    lifecycle.transactions().mutationAdmission().requireDirectMutationPermitted();
    if (Files.notExists(context.bookPath())) {
      return new TaxRegistrationMutationOutcome.Rejected(
          new TaxDeclarationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          try {
            if (!lifecycle.isInitializedBook(activeDatabase)) {
              return new TaxRegistrationMutationOutcome.Rejected(
                  new TaxDeclarationRejection.BookNotInitialized());
            }

            SqliteAttestedWriteAdmission admission =
                lifecycle
                    .transactions()
                    .mutationAdmission()
                    .admitDirectAttestedWrite(activeDatabase);
            transactionOwnership = admission.transactionOwnership();
            Optional<DeclaredTaxRegistration> existingRegistration =
                SqliteTaxStatementQueries.findOneTaxRegistration(
                    activeDatabase, command.taxRegistrationId());
            DeclaredTaxRegistration candidate =
                SqliteTaxRegistrationMutationMapper.declaredTaxRegistrationSnapshot(
                    command,
                    existingRegistration
                        .map(DeclaredTaxRegistration::declaredAt)
                        .orElse(declaredAt));
            if (existingRegistration.filter(candidate::equals).isPresent()) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
              return new TaxRegistrationMutationOutcome.Unchanged(
                  existingRegistration.orElseThrow());
            }
            var preimages =
                SqliteTaxRegistrationMutationMapper.attestationPreimages(
                    command,
                    candidate,
                    existingRegistration.isPresent(),
                    DECLARE_TAX_REGISTRATION_OPERATION);
            SqliteMutationWriter.upsertTaxRegistration(activeDatabase, candidate);
            DeclaredTaxRegistration persistedRegistration =
                SqliteTaxStatementQueries.findOneTaxRegistration(
                        activeDatabase, command.taxRegistrationId())
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Persisted SQLite tax registration disappeared after write: "
                                    + command.taxRegistrationId().value()));
            AttestationAppendOutcome.Appended attestationAppend =
                SqliteAttestationEvidenceStore.appendAuthorized(
                        activeDatabase,
                        admission.observedHead(),
                        DECLARE_TAX_REGISTRATION_OPERATION,
                        declaredAt,
                        preimages,
                        attestationAuthorizer)
                    .requireAppended();
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            return existingRegistration.isPresent()
                ? new TaxRegistrationMutationOutcome.Updated(
                    persistedRegistration, attestationAppend)
                : new TaxRegistrationMutationOutcome.Declared(
                    persistedRegistration, attestationAppend);
          } catch (SqliteNativeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw SqliteStoreOperations.sqliteFailure(
                "Failed to declare SQLite tax registration.", exception);
          } catch (RuntimeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw exception;
          }
        });
  }

  PlanTaxRegistrationMutationOutcome declareTaxRegistrationForPlan(
      DeclareTaxRegistrationCommand command,
      Instant declaredAt,
      dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer attestationAuthorizer) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(declaredAt, "declaredAt");
    Objects.requireNonNull(attestationAuthorizer, "attestationAuthorizer");
    lifecycle.transactions().mutationAdmission().requirePlanChildMutation(attestationAuthorizer);
    if (Files.notExists(context.bookPath())) {
      return new PlanTaxRegistrationMutationOutcome.Rejected(
          new TaxDeclarationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          try {
            if (!lifecycle.isInitializedBook(activeDatabase)) {
              return new PlanTaxRegistrationMutationOutcome.Rejected(
                  new TaxDeclarationRejection.BookNotInitialized());
            }
            SqliteAttestedWriteAdmission admission =
                lifecycle
                    .transactions()
                    .mutationAdmission()
                    .admitPlanChildWrite(activeDatabase, attestationAuthorizer);
            transactionOwnership = admission.transactionOwnership();
            Optional<DeclaredTaxRegistration> existingRegistration =
                SqliteTaxStatementQueries.findOneTaxRegistration(
                    activeDatabase, command.taxRegistrationId());
            DeclaredTaxRegistration candidate =
                SqliteTaxRegistrationMutationMapper.declaredTaxRegistrationSnapshot(
                    command,
                    existingRegistration
                        .map(DeclaredTaxRegistration::declaredAt)
                        .orElse(declaredAt));
            if (existingRegistration.filter(candidate::equals).isPresent()) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
              return new PlanTaxRegistrationMutationOutcome.Unchanged(
                  existingRegistration.orElseThrow());
            }
            var preimages =
                SqliteTaxRegistrationMutationMapper.attestationPreimages(
                    command,
                    candidate,
                    existingRegistration.isPresent(),
                    DECLARE_TAX_REGISTRATION_OPERATION);
            SqliteMutationWriter.upsertTaxRegistration(activeDatabase, candidate);
            DeclaredTaxRegistration persistedRegistration =
                SqliteTaxStatementQueries.findOneTaxRegistration(
                        activeDatabase, command.taxRegistrationId())
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Persisted SQLite tax registration disappeared after write: "
                                    + command.taxRegistrationId().value()));
            lifecycle
                .transactions()
                .mutationAdmission()
                .recordCompletedPlanChild(
                    attestationAuthorizer,
                    DECLARE_TAX_REGISTRATION_OPERATION.wireToken(),
                    preimages);
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            return existingRegistration.isPresent()
                ? new PlanTaxRegistrationMutationOutcome.Updated(persistedRegistration)
                : new PlanTaxRegistrationMutationOutcome.Declared(persistedRegistration);
          } catch (SqliteNativeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            IllegalStateException failure =
                SqliteStoreOperations.sqliteFailure(
                    "Failed to declare SQLite ledger-plan tax registration.", exception);
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
}
