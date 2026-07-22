package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.attestation.AttestationAccountMutationIntent;
import dev.erst.fingrind.core.attestation.AttestationAccountMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationAccountSnapshot;
import dev.erst.fingrind.core.attestation.AttestationEffectMutation;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.executor.bookkeeping.AccountAmendmentOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryLifecyclePolicy;
import dev.erst.fingrind.executor.bookkeeping.AccountRetirementOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Account Registry mutations over one SQLite-backed book session. */
final class SqliteStoreAccountRegistryMutationOperations {
  private static final AttestationOperationKind DECLARE_ACCOUNT_OPERATION =
      AttestationOperationKind.DECLARE_ACCOUNT;
  private static final AttestationOperationKind AMEND_ACCOUNT_OPERATION =
      AttestationOperationKind.AMEND_ACCOUNT;
  private static final AttestationOperationKind RETIRE_ACCOUNT_OPERATION =
      AttestationOperationKind.RETIRE_ACCOUNT;

  /** One mutation callback that borrows the session-owned SQLite handle without closing it. */
  @FunctionalInterface
  private interface BorrowedDatabaseAction<T> {
    /** Runs one mutation callback against the active SQLite handle. */
    T run(SqliteNativeDatabase activeDatabase);
  }

  /** One admitted mutation that is committed with its attestation evidence. */
  @FunctionalInterface
  private interface AttestedMutation<T> {
    /** Persists the mutation after its chain head was observed and the write transaction began. */
    T run(
        SqliteNativeDatabase activeDatabase,
        SqliteAttestationEvidenceStore.ObservedHead observedHead);
  }

  private final SqliteStoreContext context;
  private final SqliteStoreLifecycle lifecycle;

  SqliteStoreAccountRegistryMutationOperations(
      SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    this.context = Objects.requireNonNull(context, "context");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
  }

  AccountDeclarationOutcome declareAccount(
      AccountDeclaration declaration,
      Instant declaredAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    Objects.requireNonNull(declaration, "declaration");
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    if (Files.notExists(context.bookPath())) {
      return new AccountDeclarationOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          return executeAttestedMutation(
              activeDatabase,
              () ->
                  new AccountDeclarationOutcome.Rejected(
                      new BookkeepingAdministrationRejection.BookNotInitialized()),
              "Failed to declare SQLite book account.",
              (database, observedHead) -> {
                Optional<RegisteredAccount> existingAccount =
                    SqliteAccountStatementQueries.findOneAccount(
                        database, declaration.accountCode());
                AccountDeclarationOutcome declarationOutcome =
                    RegisteredAccount.declare(
                        existingAccount.orElse(null), declaration, declaredAt);
                if (declarationOutcome instanceof AccountDeclarationOutcome.Rejected rejected) {
                  return rejected;
                }
                if (declarationOutcome instanceof AccountDeclarationOutcome.Unchanged unchanged) {
                  return unchanged;
                }
                RegisteredAccount declaredAccount = declaredAccount(declarationOutcome);
                SqliteAttestationEvidenceStore.appendAuthorized(
                    database,
                    observedHead,
                    DECLARE_ACCOUNT_OPERATION,
                    declaredAt,
                    AttestationAccountMutationProjection.project(
                        AttestationAccountMutationIntent.DECLARATION,
                        DECLARE_ACCOUNT_OPERATION.wireToken(),
                        requestedSnapshot(declaration),
                        snapshot(declaredAccount),
                        declarationMutation(declarationOutcome)),
                    attestationAuthorizer);
                SqliteAccountRegistryMutationWriter.upsertAccount(database, declaredAccount);
                SqliteAuditEventWriter.insertAuditEvent(
                    database, accountAuditEvent(declaredAt, declarationOutcome));
                return declarationOutcome;
              });
        });
  }

  AccountAmendmentOutcome amendAccount(
      AccountDeclaration amendment,
      Instant amendedAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    Objects.requireNonNull(amendment, "amendment");
    Objects.requireNonNull(amendedAt, "amendedAt");
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    if (Files.notExists(context.bookPath())) {
      return new AccountAmendmentOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          return executeAttestedMutation(
              activeDatabase,
              () ->
                  new AccountAmendmentOutcome.Rejected(
                      new BookkeepingAdministrationRejection.BookNotInitialized()),
              "Failed to amend SQLite book account.",
              (database, observedHead) -> {
                Optional<RegisteredAccount> existingAccount =
                    SqliteAccountStatementQueries.findOneAccount(database, amendment.accountCode());
                AccountAmendmentOutcome outcome =
                    AccountRegistryLifecyclePolicy.amend(
                        existingAccount.orElse(null),
                        amendment,
                        SqliteAccountLifecycleQueries.amendmentDependencies(
                            database, amendment.accountCode()));
                if (outcome instanceof AccountAmendmentOutcome.Rejected
                    || outcome instanceof AccountAmendmentOutcome.Unchanged) {
                  return outcome;
                }
                AccountAmendmentOutcome.Amended amended = (AccountAmendmentOutcome.Amended) outcome;
                SqliteAttestationEvidenceStore.appendAuthorized(
                    database,
                    observedHead,
                    AMEND_ACCOUNT_OPERATION,
                    amendedAt,
                    AttestationAccountMutationProjection.project(
                        AttestationAccountMutationIntent.AMENDMENT,
                        AMEND_ACCOUNT_OPERATION.wireToken(),
                        requestedSnapshot(amendment),
                        snapshot(amended.account()),
                        AttestationEffectMutation.AMEND),
                    attestationAuthorizer);
                SqliteAccountRegistryMutationWriter.amendAccount(database, amended.account());
                SqliteAuditEventWriter.insertAuditEvent(
                    database,
                    BookAuditEvent.accountAmended(amendedAt, amended.account().accountCode()));
                return outcome;
              });
        });
  }

  AccountRetirementOutcome retireAccount(
      AccountCode accountCode,
      Instant retiredAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(retiredAt, "retiredAt");
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    if (Files.notExists(context.bookPath())) {
      return new AccountRetirementOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          return executeAttestedMutation(
              activeDatabase,
              () ->
                  new AccountRetirementOutcome.Rejected(
                      new BookkeepingAdministrationRejection.BookNotInitialized()),
              "Failed to retire SQLite book account.",
              (database, observedHead) -> {
                Optional<RegisteredAccount> existingAccount =
                    SqliteAccountStatementQueries.findOneAccount(database, accountCode);
                AccountRetirementOutcome outcome =
                    AccountRegistryLifecyclePolicy.retire(
                        accountCode,
                        existingAccount.orElse(null),
                        SqliteAccountLifecycleQueries.retirementDependencies(database, accountCode),
                        SqliteAccountLifecycleQueries.currentBalanceZero(database, accountCode));
                if (outcome instanceof AccountRetirementOutcome.Rejected
                    || outcome instanceof AccountRetirementOutcome.Unchanged) {
                  return outcome;
                }
                AccountRetirementOutcome.Retired retired =
                    (AccountRetirementOutcome.Retired) outcome;
                SqliteAttestationEvidenceStore.appendAuthorized(
                    database,
                    observedHead,
                    RETIRE_ACCOUNT_OPERATION,
                    retiredAt,
                    AttestationAccountMutationProjection.project(
                        AttestationAccountMutationIntent.RETIREMENT,
                        RETIRE_ACCOUNT_OPERATION.wireToken(),
                        snapshot(existingAccount.orElseThrow()),
                        snapshot(retired.account()),
                        AttestationEffectMutation.RETIRE),
                    attestationAuthorizer);
                SqliteAccountRegistryMutationWriter.retireAccount(database, accountCode);
                SqliteAuditEventWriter.insertAuditEvent(
                    database,
                    BookAuditEvent.accountRetired(retiredAt, retired.account().accountCode()));
                return outcome;
              });
        });
  }

  static RegisteredAccount declaredAccount(AccountDeclarationOutcome declarationOutcome) {
    return switch (Objects.requireNonNull(declarationOutcome, "declarationOutcome")) {
      case AccountDeclarationOutcome.Declared declared -> declared.account();
      case AccountDeclarationOutcome.Reactivated reactivated -> reactivated.account();
      case AccountDeclarationOutcome.Renamed renamed -> renamed.account();
      case AccountDeclarationOutcome.Unchanged unchanged -> unchanged.account();
      case AccountDeclarationOutcome.Rejected rejected ->
          throw new IllegalArgumentException(
              "Rejected account declarations do not carry a durable account snapshot: "
                  + rejected.rejection());
    };
  }

  static BookAuditEvent accountAuditEvent(
      Instant recordedAt, AccountDeclarationOutcome declarationOutcome) {
    return switch (Objects.requireNonNull(declarationOutcome, "declarationOutcome")) {
      case AccountDeclarationOutcome.Declared declared ->
          BookAuditEvent.accountDeclared(recordedAt, declared.account().accountCode());
      case AccountDeclarationOutcome.Reactivated reactivated ->
          BookAuditEvent.accountReactivated(recordedAt, reactivated.account().accountCode());
      case AccountDeclarationOutcome.Renamed renamed ->
          BookAuditEvent.accountRenamed(recordedAt, renamed.account().accountCode());
      case AccountDeclarationOutcome.Unchanged _ ->
          throw new IllegalArgumentException("Unchanged account declarations do not append audit.");
      case AccountDeclarationOutcome.Rejected rejected ->
          throw new IllegalArgumentException(
              "Rejected account declarations do not append audit: " + rejected.rejection());
    };
  }

  private static AttestationAccountSnapshot requestedSnapshot(AccountDeclaration declaration) {
    return new AttestationAccountSnapshot(
        declaration.accountCode(),
        declaration.accountName(),
        declaration.accountType(),
        declaration.accountTaxonomy(),
        declaration.unitOfMeasure(),
        true);
  }

  private static AttestationAccountSnapshot snapshot(RegisteredAccount account) {
    return new AttestationAccountSnapshot(
        account.accountCode(),
        account.accountName(),
        account.accountType(),
        account.accountTaxonomy(),
        account.unitOfMeasure(),
        account.active());
  }

  private static AttestationEffectMutation declarationMutation(AccountDeclarationOutcome outcome) {
    return switch (outcome) {
      case AccountDeclarationOutcome.Declared _ -> AttestationEffectMutation.CREATE;
      case AccountDeclarationOutcome.Reactivated _ -> AttestationEffectMutation.REACTIVATE;
      case AccountDeclarationOutcome.Renamed _ -> AttestationEffectMutation.AMEND;
      case AccountDeclarationOutcome.Unchanged _ ->
          throw new IllegalArgumentException(
              "Unchanged account declarations do not append attestation.");
      case AccountDeclarationOutcome.Rejected _ ->
          throw new IllegalArgumentException(
              "Rejected account declarations do not append attestation.");
    };
  }

  private <T> T executeAttestedMutation(
      SqliteNativeDatabase activeDatabase,
      Supplier<T> bookNotInitializedOutcome,
      String failureMessage,
      AttestedMutation<T> mutation) {
    SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
    try {
      if (!lifecycle.isInitializedBook(activeDatabase)) {
        return Objects.requireNonNull(bookNotInitializedOutcome, "bookNotInitializedOutcome").get();
      }
      SqliteAttestedWriteAdmission admission =
          lifecycle.transactions().admitAttestedWrite(activeDatabase);
      transactionOwnership = admission.transactionOwnership();
      T outcome = mutation.run(activeDatabase, admission.observedHead());
      SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
      return outcome;
    } catch (SqliteNativeException exception) {
      SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
      throw SqliteStoreOperations.sqliteFailure(failureMessage, exception);
    } catch (RuntimeException exception) {
      SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
      throw exception;
    }
  }

  private <T> T withBorrowedDatabase(BorrowedDatabaseAction<T> action) {
    return action.run(lifecycle.database());
  }
}
