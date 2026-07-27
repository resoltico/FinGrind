package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.PlanTaxRegistrationMutationOutcome;
import dev.erst.fingrind.executor.bookkeeping.TaxRegistrationMutationOutcome;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Tax-registration mutations over one SQLite-backed book session. */
final class SqliteStoreAdministrationMutationOperations {
  private static final AttestationOperationKind DECLARE_TAX_REGISTRATION_OPERATION =
      AttestationOperationKind.DECLARE_TAX_REGISTRATION;

  private final SqliteStoreContext context;
  private final SqliteStoreLifecycle lifecycle;

  SqliteStoreAdministrationMutationOperations(
      SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    this.context = Objects.requireNonNull(context, "context");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
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
      AttestationPlanOperationAuthorizer attestationAuthorizer) {
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

  /** One mutation callback that borrows the session-owned SQLite handle without closing it. */
  @FunctionalInterface
  private interface BorrowedDatabaseAction<T> {
    /** Runs one mutation callback against the active SQLite handle. */
    T run(SqliteNativeDatabase activeDatabase);
  }
}
