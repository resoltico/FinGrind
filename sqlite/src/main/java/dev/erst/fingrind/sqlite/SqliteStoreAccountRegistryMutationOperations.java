package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.attestation.AttestationAccountMutationIntent;
import dev.erst.fingrind.core.attestation.AttestationAccountMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationEffectMutation;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.AccountAmendmentDecision;
import dev.erst.fingrind.executor.bookkeeping.AccountAmendmentOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationDecision;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryLifecyclePolicy;
import dev.erst.fingrind.executor.bookkeeping.AccountRetirementDecision;
import dev.erst.fingrind.executor.bookkeeping.AccountRetirementOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome;
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
    lifecycle.transactions().mutationAdmission().requireDirectMutationPermitted();
    if (Files.notExists(context.bookPath())) {
      return new AccountDeclarationOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        database ->
            attestedMutationExecutor.executeDirect(
                database,
                () ->
                    new AccountDeclarationOutcome.Rejected(
                        new BookkeepingAdministrationRejection.BookNotInitialized()),
                "Failed to declare SQLite book account.",
                (activeDatabase, observedHead) -> {
                  AccountDeclarationDecision declarationDecision =
                      declarationDecision(activeDatabase, declaration, declaredAt);
                  if (declarationDecision instanceof AccountDeclarationDecision.Rejected rejected) {
                    return new AccountDeclarationOutcome.Rejected(rejected.rejection());
                  }
                  if (declarationDecision
                      instanceof AccountDeclarationDecision.Unchanged unchanged) {
                    return new AccountDeclarationOutcome.Unchanged(unchanged.account());
                  }
                  RegisteredAccount declaredAccount =
                      SqliteAccountRegistryDeclarationMapper.declaredAccount(declarationDecision);
                  var preimages =
                      SqliteAccountRegistryDeclarationMapper.declarationPreimages(
                          declaration, declarationDecision, DECLARE_ACCOUNT_OPERATION);
                  SqliteAccountRegistryMutationWriter.upsertAccount(
                      activeDatabase, declaredAccount);
                  SqliteAuditEventWriter.insertAuditEvent(
                      activeDatabase,
                      SqliteAccountRegistryDeclarationMapper.accountAuditEvent(
                          declaredAt, declarationDecision));
                  AttestationAppendOutcome.Appended attestationAppend =
                      SqliteAttestationEvidenceStore.appendAuthorized(
                              activeDatabase,
                              observedHead,
                              DECLARE_ACCOUNT_OPERATION,
                              declaredAt,
                              preimages,
                              attestationAuthorizer)
                          .requireAppended();
                  return SqliteAccountRegistryDeclarationMapper.withAttestationAppend(
                      declarationDecision, attestationAppend);
                }));
  }

  PlanAccountDeclarationOutcome declareAccountForPlan(
      AccountDeclaration declaration,
      Instant declaredAt,
      AttestationPlanOperationAuthorizer attestationAuthorizer) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    Objects.requireNonNull(declaration, "declaration");
    Objects.requireNonNull(declaredAt, "declaredAt");
    Objects.requireNonNull(attestationAuthorizer, "attestationAuthorizer");
    lifecycle.transactions().mutationAdmission().requirePlanChildMutation(attestationAuthorizer);
    if (Files.notExists(context.bookPath())) {
      return new PlanAccountDeclarationOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        database ->
            attestedMutationExecutor.executePlanChild(
                database,
                attestationAuthorizer,
                () ->
                    new PlanAccountDeclarationOutcome.Rejected(
                        new BookkeepingAdministrationRejection.BookNotInitialized()),
                "Failed to declare SQLite ledger-plan account.",
                (activeDatabase, ignoredObservedHead) -> {
                  AccountDeclarationDecision decision =
                      declarationDecision(activeDatabase, declaration, declaredAt);
                  if (decision instanceof AccountDeclarationDecision.Rejected rejected) {
                    return new PlanAccountDeclarationOutcome.Rejected(rejected.rejection());
                  }
                  if (decision instanceof AccountDeclarationDecision.Unchanged unchanged) {
                    return new PlanAccountDeclarationOutcome.Unchanged(unchanged.account());
                  }
                  RegisteredAccount declaredAccount =
                      SqliteAccountRegistryDeclarationMapper.declaredAccount(decision);
                  var preimages =
                      SqliteAccountRegistryDeclarationMapper.declarationPreimages(
                          declaration, decision, DECLARE_ACCOUNT_OPERATION);
                  SqliteAccountRegistryMutationWriter.upsertAccount(
                      activeDatabase, declaredAccount);
                  SqliteAuditEventWriter.insertAuditEvent(
                      activeDatabase,
                      SqliteAccountRegistryDeclarationMapper.accountAuditEvent(
                          declaredAt, decision));
                  lifecycle
                      .transactions()
                      .mutationAdmission()
                      .recordCompletedPlanChild(
                          attestationAuthorizer, DECLARE_ACCOUNT_OPERATION.wireToken(), preimages);
                  return SqliteAccountRegistryDeclarationMapper.planOutcome(decision);
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
    lifecycle.transactions().mutationAdmission().requireDirectMutationPermitted();
    if (Files.notExists(context.bookPath())) {
      return new AccountAmendmentOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        database ->
            attestedMutationExecutor.executeDirect(
                database,
                () ->
                    new AccountAmendmentOutcome.Rejected(
                        new BookkeepingAdministrationRejection.BookNotInitialized()),
                "Failed to amend SQLite book account.",
                (activeDatabase, observedHead) -> {
                  Optional<RegisteredAccount> existingAccount =
                      SqliteAccountStatementQueries.findOneAccount(
                          activeDatabase, amendment.accountCode());
                  AccountAmendmentDecision decision =
                      AccountRegistryLifecyclePolicy.amend(
                          existingAccount.orElse(null),
                          amendment,
                          SqliteAccountLifecycleQueries.amendmentDependencies(
                              activeDatabase, amendment.accountCode()));
                  if (decision instanceof AccountAmendmentDecision.Rejected rejected) {
                    return new AccountAmendmentOutcome.Rejected(rejected.rejection());
                  }
                  if (decision instanceof AccountAmendmentDecision.Unchanged unchanged) {
                    return new AccountAmendmentOutcome.Unchanged(unchanged.account());
                  }
                  AccountAmendmentDecision.Amended amended =
                      (AccountAmendmentDecision.Amended) decision;
                  var preimages =
                      AttestationAccountMutationProjection.project(
                          AttestationAccountMutationIntent.AMENDMENT,
                          AMEND_ACCOUNT_OPERATION.wireToken(),
                          SqliteAccountRegistryDeclarationMapper.requestedSnapshot(amendment),
                          SqliteAccountRegistryDeclarationMapper.snapshot(amended.account()),
                          AttestationEffectMutation.AMEND);
                  SqliteAccountRegistryMutationWriter.amendAccount(
                      activeDatabase, amended.account());
                  SqliteAuditEventWriter.insertAuditEvent(
                      activeDatabase,
                      BookAuditEvent.accountAmended(amendedAt, amended.account().accountCode()));
                  AttestationAppendOutcome.Appended attestationAppend =
                      SqliteAttestationEvidenceStore.appendAuthorized(
                              activeDatabase,
                              observedHead,
                              AMEND_ACCOUNT_OPERATION,
                              amendedAt,
                              preimages,
                              attestationAuthorizer)
                          .requireAppended();
                  return new AccountAmendmentOutcome.Amended(amended.account(), attestationAppend);
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
    lifecycle.transactions().mutationAdmission().requireDirectMutationPermitted();
    if (Files.notExists(context.bookPath())) {
      return new AccountRetirementOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        database ->
            attestedMutationExecutor.executeDirect(
                database,
                () ->
                    new AccountRetirementOutcome.Rejected(
                        new BookkeepingAdministrationRejection.BookNotInitialized()),
                "Failed to retire SQLite book account.",
                (activeDatabase, observedHead) -> {
                  Optional<RegisteredAccount> existingAccount =
                      SqliteAccountStatementQueries.findOneAccount(activeDatabase, accountCode);
                  AccountRetirementDecision decision =
                      AccountRegistryLifecyclePolicy.retire(
                          accountCode,
                          existingAccount.orElse(null),
                          SqliteAccountLifecycleQueries.retirementDependencies(
                              activeDatabase, accountCode),
                          SqliteAccountLifecycleQueries.currentBalanceZero(
                              activeDatabase, accountCode));
                  if (decision instanceof AccountRetirementDecision.Rejected rejected) {
                    return new AccountRetirementOutcome.Rejected(rejected.rejection());
                  }
                  if (decision instanceof AccountRetirementDecision.Unchanged unchanged) {
                    return new AccountRetirementOutcome.Unchanged(unchanged.account());
                  }
                  AccountRetirementDecision.Retired retired =
                      (AccountRetirementDecision.Retired) decision;
                  var preimages =
                      AttestationAccountMutationProjection.project(
                          AttestationAccountMutationIntent.RETIREMENT,
                          RETIRE_ACCOUNT_OPERATION.wireToken(),
                          SqliteAccountRegistryDeclarationMapper.snapshot(
                              existingAccount.orElseThrow()),
                          SqliteAccountRegistryDeclarationMapper.snapshot(retired.account()),
                          AttestationEffectMutation.RETIRE);
                  SqliteAccountRegistryMutationWriter.retireAccount(activeDatabase, accountCode);
                  SqliteAuditEventWriter.insertAuditEvent(
                      activeDatabase,
                      BookAuditEvent.accountRetired(retiredAt, retired.account().accountCode()));
                  AttestationAppendOutcome.Appended attestationAppend =
                      SqliteAttestationEvidenceStore.appendAuthorized(
                              activeDatabase,
                              observedHead,
                              RETIRE_ACCOUNT_OPERATION,
                              retiredAt,
                              preimages,
                              attestationAuthorizer)
                          .requireAppended();
                  return new AccountRetirementOutcome.Retired(retired.account(), attestationAppend);
                }));
  }

  private static AccountDeclarationDecision declarationDecision(
      SqliteNativeDatabase activeDatabase, AccountDeclaration declaration, Instant declaredAt) {
    Optional<RegisteredAccount> existingAccount =
        SqliteAccountStatementQueries.findOneAccount(activeDatabase, declaration.accountCode());
    return RegisteredAccount.declare(existingAccount.orElse(null), declaration, declaredAt);
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
