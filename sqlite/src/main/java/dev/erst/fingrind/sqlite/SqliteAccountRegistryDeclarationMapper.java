package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationAccountMutationIntent;
import dev.erst.fingrind.core.attestation.AttestationAccountMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationAccountSnapshot;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationEffectMutation;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationDecision;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.util.Objects;

/** Maps account-registry declaration decisions to durable and attested mutation facts. */
final class SqliteAccountRegistryDeclarationMapper {
  private SqliteAccountRegistryDeclarationMapper() {}

  static RegisteredAccount declaredAccount(AccountDeclarationDecision declarationDecision) {
    return switch (Objects.requireNonNull(declarationDecision, "declarationDecision")) {
      case AccountDeclarationDecision.Declared declared -> declared.account();
      case AccountDeclarationDecision.Reactivated reactivated -> reactivated.account();
      case AccountDeclarationDecision.Renamed renamed -> renamed.account();
      case AccountDeclarationDecision.Unchanged unchanged -> unchanged.account();
      case AccountDeclarationDecision.Rejected rejected ->
          throw new IllegalArgumentException(
              "Rejected account declarations do not carry a durable account snapshot: "
                  + rejected.rejection());
    };
  }

  static AccountDeclarationOutcome withAttestationAppend(
      AccountDeclarationDecision decision, AttestationAppendOutcome.Appended attestationAppend) {
    return switch (Objects.requireNonNull(decision, "decision")) {
      case AccountDeclarationDecision.Declared declared ->
          new AccountDeclarationOutcome.Declared(declared.account(), attestationAppend);
      case AccountDeclarationDecision.Reactivated reactivated ->
          new AccountDeclarationOutcome.Reactivated(reactivated.account(), attestationAppend);
      case AccountDeclarationDecision.Renamed renamed ->
          new AccountDeclarationOutcome.Renamed(renamed.account(), attestationAppend);
      case AccountDeclarationDecision.Unchanged _ ->
          throw new IllegalArgumentException(
              "An unchanged account declaration must not receive an attestation append outcome.");
      case AccountDeclarationDecision.Rejected _ ->
          throw new IllegalArgumentException(
              "A rejected account declaration must not receive an attestation append outcome.");
    };
  }

  static BookAuditEvent accountAuditEvent(
      Instant recordedAt, AccountDeclarationDecision declarationDecision) {
    return switch (Objects.requireNonNull(declarationDecision, "declarationDecision")) {
      case AccountDeclarationDecision.Declared declared ->
          BookAuditEvent.accountDeclared(recordedAt, declared.account().accountCode());
      case AccountDeclarationDecision.Reactivated reactivated ->
          BookAuditEvent.accountReactivated(recordedAt, reactivated.account().accountCode());
      case AccountDeclarationDecision.Renamed renamed ->
          BookAuditEvent.accountRenamed(recordedAt, renamed.account().accountCode());
      case AccountDeclarationDecision.Unchanged _ ->
          throw new IllegalArgumentException("Unchanged account declarations do not append audit.");
      case AccountDeclarationDecision.Rejected rejected ->
          throw new IllegalArgumentException(
              "Rejected account declarations do not append audit: " + rejected.rejection());
    };
  }

  static AttestationOperationPreimages declarationPreimages(
      AccountDeclaration declaration,
      AccountDeclarationDecision decision,
      AttestationOperationKind operationKind) {
    Objects.requireNonNull(operationKind, "operationKind");
    return AttestationAccountMutationProjection.project(
        AttestationAccountMutationIntent.DECLARATION,
        operationKind.wireToken(),
        requestedSnapshot(declaration),
        snapshot(declaredAccount(decision)),
        declarationMutation(decision));
  }

  static PlanAccountDeclarationOutcome planOutcome(AccountDeclarationDecision decision) {
    return switch (Objects.requireNonNull(decision, "decision")) {
      case AccountDeclarationDecision.Declared declared ->
          new PlanAccountDeclarationOutcome.Declared(declared.account());
      case AccountDeclarationDecision.Reactivated reactivated ->
          new PlanAccountDeclarationOutcome.Reactivated(reactivated.account());
      case AccountDeclarationDecision.Renamed renamed ->
          new PlanAccountDeclarationOutcome.Renamed(renamed.account());
      case AccountDeclarationDecision.Unchanged unchanged ->
          new PlanAccountDeclarationOutcome.Unchanged(unchanged.account());
      case AccountDeclarationDecision.Rejected rejected ->
          new PlanAccountDeclarationOutcome.Rejected(rejected.rejection());
    };
  }

  static AttestationEffectMutation declarationMutation(AccountDeclarationDecision decision) {
    return switch (Objects.requireNonNull(decision, "decision")) {
      case AccountDeclarationDecision.Declared _ -> AttestationEffectMutation.CREATE;
      case AccountDeclarationDecision.Reactivated _ -> AttestationEffectMutation.REACTIVATE;
      case AccountDeclarationDecision.Renamed _ -> AttestationEffectMutation.AMEND;
      case AccountDeclarationDecision.Unchanged _ ->
          throw new IllegalArgumentException(
              "Unchanged account declarations do not append attestation.");
      case AccountDeclarationDecision.Rejected _ ->
          throw new IllegalArgumentException(
              "Rejected account declarations do not append attestation.");
    };
  }

  static AttestationAccountSnapshot requestedSnapshot(AccountDeclaration declaration) {
    return new AttestationAccountSnapshot(
        declaration.accountCode(),
        declaration.accountName(),
        declaration.accountType(),
        declaration.accountTaxonomy(),
        declaration.unitOfMeasure(),
        true);
  }

  static AttestationAccountSnapshot snapshot(RegisteredAccount account) {
    return new AttestationAccountSnapshot(
        account.accountCode(),
        account.accountName(),
        account.accountType(),
        account.accountTaxonomy(),
        account.unitOfMeasure(),
        account.active());
  }
}
