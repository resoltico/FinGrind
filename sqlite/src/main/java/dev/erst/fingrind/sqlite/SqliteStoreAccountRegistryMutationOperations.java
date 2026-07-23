package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.attestation.AttestationAccountMutationIntent;
import dev.erst.fingrind.core.attestation.AttestationAccountMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationAccountSnapshot;
import dev.erst.fingrind.core.attestation.AttestationEffectMutation;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.executor.AttestationCommitProjection;
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

/** Account Registry mutations over one SQLite-backed book session. */
final class SqliteStoreAccountRegistryMutationOperations {
  private static final AttestationOperationKind DECLARE_ACCOUNT_OPERATION =
      AttestationOperationKind.DECLARE_ACCOUNT;
  private static final AttestationOperationKind AMEND_ACCOUNT_OPERATION =
      AttestationOperationKind.AMEND_ACCOUNT;
  private static final AttestationOperationKind RETIRE_ACCOUNT_OPERATION =
      AttestationOperationKind.RETIRE_ACCOUNT;

  private final SqliteStoreContext context;
  private final SqliteStoreLifecycle lifecycle;
  private final SqliteAccountRegistryAttestedMutationExecutor attestedMutationExecutor;

  SqliteStoreAccountRegistryMutationOperations(
      SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    this.context = Objects.requireNonNull(context, "context");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    attestedMutationExecutor = new SqliteAccountRegistryAttestedMutationExecutor(this.lifecycle);
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
        database ->
            attestedMutationExecutor.execute(
                database,
                () ->
                    new AccountDeclarationOutcome.Rejected(
                        new BookkeepingAdministrationRejection.BookNotInitialized()),
                "Failed to declare SQLite book account.",
                (activeDatabase, observedHead) -> {
                  Optional<RegisteredAccount> existingAccount =
                      SqliteAccountStatementQueries.findOneAccount(
                          activeDatabase, declaration.accountCode());
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
                  var verification =
                      SqliteAttestationEvidenceStore.appendAuthorized(
                          activeDatabase,
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
                  SqliteAccountRegistryMutationWriter.upsertAccount(
                      activeDatabase, declaredAccount);
                  SqliteAuditEventWriter.insertAuditEvent(
                      activeDatabase, accountAuditEvent(declaredAt, declarationOutcome));
                  return withAttestationCommit(
                      declarationOutcome,
                      AttestationCommitProjection.fromVerifiedAppend(verification));
                }));
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
        database ->
            attestedMutationExecutor.execute(
                database,
                () ->
                    new AccountAmendmentOutcome.Rejected(
                        new BookkeepingAdministrationRejection.BookNotInitialized()),
                "Failed to amend SQLite book account.",
                (activeDatabase, observedHead) -> {
                  Optional<RegisteredAccount> existingAccount =
                      SqliteAccountStatementQueries.findOneAccount(
                          activeDatabase, amendment.accountCode());
                  AccountAmendmentOutcome outcome =
                      AccountRegistryLifecyclePolicy.amend(
                          existingAccount.orElse(null),
                          amendment,
                          SqliteAccountLifecycleQueries.amendmentDependencies(
                              activeDatabase, amendment.accountCode()));
                  if (outcome instanceof AccountAmendmentOutcome.Rejected
                      || outcome instanceof AccountAmendmentOutcome.Unchanged) {
                    return outcome;
                  }
                  AccountAmendmentOutcome.Amended amended =
                      (AccountAmendmentOutcome.Amended) outcome;
                  var verification =
                      SqliteAttestationEvidenceStore.appendAuthorized(
                          activeDatabase,
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
                  SqliteAccountRegistryMutationWriter.amendAccount(
                      activeDatabase, amended.account());
                  SqliteAuditEventWriter.insertAuditEvent(
                      activeDatabase,
                      BookAuditEvent.accountAmended(amendedAt, amended.account().accountCode()));
                  return new AccountAmendmentOutcome.Amended(
                      amended.account(),
                      AttestationCommitProjection.fromVerifiedAppend(verification));
                }));
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
        database ->
            attestedMutationExecutor.execute(
                database,
                () ->
                    new AccountRetirementOutcome.Rejected(
                        new BookkeepingAdministrationRejection.BookNotInitialized()),
                "Failed to retire SQLite book account.",
                (activeDatabase, observedHead) -> {
                  Optional<RegisteredAccount> existingAccount =
                      SqliteAccountStatementQueries.findOneAccount(activeDatabase, accountCode);
                  AccountRetirementOutcome outcome =
                      AccountRegistryLifecyclePolicy.retire(
                          accountCode,
                          existingAccount.orElse(null),
                          SqliteAccountLifecycleQueries.retirementDependencies(
                              activeDatabase, accountCode),
                          SqliteAccountLifecycleQueries.currentBalanceZero(
                              activeDatabase, accountCode));
                  if (outcome instanceof AccountRetirementOutcome.Rejected
                      || outcome instanceof AccountRetirementOutcome.Unchanged) {
                    return outcome;
                  }
                  AccountRetirementOutcome.Retired retired =
                      (AccountRetirementOutcome.Retired) outcome;
                  var verification =
                      SqliteAttestationEvidenceStore.appendAuthorized(
                          activeDatabase,
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
                  SqliteAccountRegistryMutationWriter.retireAccount(activeDatabase, accountCode);
                  SqliteAuditEventWriter.insertAuditEvent(
                      activeDatabase,
                      BookAuditEvent.accountRetired(retiredAt, retired.account().accountCode()));
                  return new AccountRetirementOutcome.Retired(
                      retired.account(),
                      AttestationCommitProjection.fromVerifiedAppend(verification));
                }));
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

  static AccountDeclarationOutcome withAttestationCommit(
      AccountDeclarationOutcome outcome,
      dev.erst.fingrind.contract.bookkeeping.AttestationCommit attestationCommit) {
    return switch (outcome) {
      case AccountDeclarationOutcome.Declared declared ->
          new AccountDeclarationOutcome.Declared(declared.account(), attestationCommit);
      case AccountDeclarationOutcome.Reactivated reactivated ->
          new AccountDeclarationOutcome.Reactivated(reactivated.account(), attestationCommit);
      case AccountDeclarationOutcome.Renamed renamed ->
          new AccountDeclarationOutcome.Renamed(renamed.account(), attestationCommit);
      case AccountDeclarationOutcome.Unchanged _ ->
          throw new IllegalArgumentException(
              "An unchanged account declaration must not receive an attestation commitment.");
      case AccountDeclarationOutcome.Rejected _ ->
          throw new IllegalArgumentException(
              "A rejected account declaration must not receive an attestation commitment.");
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

  private <T> T withBorrowedDatabase(BorrowedDatabaseOperation<T> operation) {
    return Objects.requireNonNull(operation, "operation").run(lifecycle.database());
  }

  /**
   * One callback that borrows the session-owned database without taking responsibility for close.
   */
  @FunctionalInterface
  private interface BorrowedDatabaseOperation<T> {
    /** Runs the operation against the session-owned database. */
    T run(SqliteNativeDatabase database);
  }
}
