package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Administrative mutation operations over one SQLite-backed book session. */
final class SqliteStoreAdministrationMutationOperations {
  /** One mutation callback that borrows the session-owned SQLite handle without closing it. */
  @FunctionalInterface
  private interface BorrowedDatabaseAction<T> {
    /** Runs one mutation callback against the active SQLite handle. */
    T run(SqliteNativeDatabase activeDatabase);
  }

  private final SqliteStoreContext context;
  private final SqliteStoreLifecycle lifecycle;

  SqliteStoreAdministrationMutationOperations(
      SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    this.context = Objects.requireNonNull(context, "context");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
  }

  BookOpeningOutcome openBook(
      Instant initializedAt, BookIdentity bookIdentity, List<AccountDeclaration> seededAccounts) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableInitialization();
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

            transactionOwnership = lifecycle.transactions().beginImmediateIfNeeded(activeDatabase);
            SqliteBookSchemaBootstrap.initializeBook(activeDatabase);
            SqliteBookIntegrityVerifier.recordSchemaFingerprint(activeDatabase);
            SqliteMutationWriter.insertInitializedAt(activeDatabase, initializedAt);
            SqliteMutationWriter.insertBookIdentity(activeDatabase, bookIdentity);
            for (AccountDeclaration seededAccount : seededAccounts) {
              RegisteredAccount declaredAccount =
                  RegisteredAccount.declareNew(seededAccount, initializedAt);
              SqliteAccountRegistryMutationWriter.upsertAccount(activeDatabase, declaredAccount);
            }
            SqliteAuditEventWriter.insertAuditEvent(
                activeDatabase, BookAuditEvent.bookOpened(initializedAt));
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            lifecycle.cacheState(
                new SqliteBookStateSnapshot(
                    SqliteBookContract.APPLICATION_ID,
                    SqliteBookContract.FORMAT_VERSION,
                    SqliteBookState.INITIALIZED_FINGRIND));
            lifecycle.recordExclusiveNewBookInitialization();
            return new BookOpeningOutcome.Opened(initializedAt, bookIdentity);
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

  DeclareTaxRegistrationResult declareTaxRegistration(
      DeclareTaxRegistrationCommand command, Instant declaredAt) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(declaredAt, "declaredAt");
    if (Files.notExists(context.bookPath())) {
      return new DeclareTaxRegistrationResult.Rejected(
          new TaxDeclarationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          try {
            if (!lifecycle.isInitializedBook(activeDatabase)) {
              return new DeclareTaxRegistrationResult.Rejected(
                  new TaxDeclarationRejection.BookNotInitialized());
            }

            transactionOwnership = lifecycle.transactions().beginImmediateIfNeeded(activeDatabase);
            Optional<DeclaredTaxRegistration> existingRegistration =
                SqliteTaxStatementQueries.findOneTaxRegistration(
                    activeDatabase, command.taxRegistrationId());
            DeclaredTaxRegistration candidate =
                declaredTaxRegistrationSnapshot(
                    command,
                    existingRegistration
                        .map(DeclaredTaxRegistration::declaredAt)
                        .orElse(declaredAt));
            if (existingRegistration.filter(candidate::equals).isPresent()) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
              return new DeclareTaxRegistrationResult.Unchanged(existingRegistration.orElseThrow());
            }
            SqliteMutationWriter.upsertTaxRegistration(activeDatabase, candidate);
            DeclaredTaxRegistration persistedRegistration =
                SqliteTaxStatementQueries.findOneTaxRegistration(
                        activeDatabase, command.taxRegistrationId())
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Persisted SQLite tax registration disappeared after write: "
                                    + command.taxRegistrationId().value()));
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            return existingRegistration.isPresent()
                ? new DeclareTaxRegistrationResult.Updated(persistedRegistration)
                : new DeclareTaxRegistrationResult.Declared(persistedRegistration);
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

  private static DeclaredTaxRegistration declaredTaxRegistrationSnapshot(
      DeclareTaxRegistrationCommand command, Instant snapshotDeclaredAt) {
    return new DeclaredTaxRegistration(
        command.taxRegistrationId(),
        command.taxRegistrationName(),
        command.jurisdiction(),
        command.registrationNumber(),
        command.payableAccountCode(),
        command.recoverableAccountCode(),
        command.obligationFrequency(),
        command.dueDaysAfterPeriodEnd(),
        command.taxCodes().stream()
            .sorted(Comparator.comparing(SqliteStoreAdministrationMutationOperations::taxCodeKey))
            .toList(),
        snapshotDeclaredAt);
  }

  private static String taxCodeKey(TaxCodeDefinition taxCodeDefinition) {
    return taxCodeDefinition.taxCode().value();
  }

  private <T> T withBorrowedDatabase(BorrowedDatabaseAction<T> action) {
    return action.run(lifecycle.database());
  }
}
