package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.attestation.AttestationAccountSnapshot;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationEffectMutation;
import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationDecision;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Builds attestation-facing values from the in-memory book fixture's domain-state transitions. */
final class InMemoryBookAttestationFixtureProjections {
  private static final UUID DIRECT_APPEND_BOOK_ID =
      UUID.fromString("018f0000-0000-7000-8000-000000000001");
  private static final byte[] DIRECT_APPEND_OPERATION_HEAD =
      HexFormat.of().parseHex("a".repeat(64));

  private InMemoryBookAttestationFixtureProjections() {}

  /** Returns deterministic verified-append evidence for one in-memory fixture mutation. */
  static AttestationAppendOutcome.Appended directAppend() {
    return new AttestationAppendOutcome.Appended(
        new AttestationVerification(
            DIRECT_APPEND_BOOK_ID,
            BigInteger.ONE,
            DIRECT_APPEND_OPERATION_HEAD,
            new byte[32],
            List.of()));
  }

  static AttestationRegistryInspection syntheticTrustRoot(BookIdentity bookIdentity) {
    return new AttestationRegistryInspection(
        UUID.nameUUIDFromBytes(bookIdentity.toString().getBytes(StandardCharsets.UTF_8)),
        BigInteger.ZERO,
        "0".repeat(64),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  static AttestationCommit attestationCommit(AttestationRegistryInspection attestationTrustRoot) {
    return new AttestationCommit(
        attestationTrustRoot.headOrder(), attestationTrustRoot.operationHeadHex());
  }

  static AccountDeclarationOutcome declarationOutcome(
      AccountDeclarationDecision decision, AttestationAppendOutcome.Appended attestationAppend) {
    return switch (decision) {
      case AccountDeclarationDecision.Declared declared ->
          new AccountDeclarationOutcome.Declared(declared.account(), attestationAppend);
      case AccountDeclarationDecision.Reactivated reactivated ->
          new AccountDeclarationOutcome.Reactivated(reactivated.account(), attestationAppend);
      case AccountDeclarationDecision.Renamed renamed ->
          new AccountDeclarationOutcome.Renamed(renamed.account(), attestationAppend);
      case AccountDeclarationDecision.Unchanged unchanged ->
          new AccountDeclarationOutcome.Unchanged(unchanged.account());
      case AccountDeclarationDecision.Rejected rejected ->
          new AccountDeclarationOutcome.Rejected(rejected.rejection());
    };
  }

  static RegisteredAccount declaredAccount(AccountDeclarationDecision decision) {
    return switch (decision) {
      case AccountDeclarationDecision.Declared declared -> declared.account();
      case AccountDeclarationDecision.Reactivated reactivated -> reactivated.account();
      case AccountDeclarationDecision.Renamed renamed -> renamed.account();
      case AccountDeclarationDecision.Unchanged _, AccountDeclarationDecision.Rejected _ ->
          throw new IllegalArgumentException(
              "Only a mutating account declaration has a newly durable account snapshot.");
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

  static AttestationEffectMutation declarationMutation(AccountDeclarationDecision decision) {
    return switch (decision) {
      case AccountDeclarationDecision.Declared _ -> AttestationEffectMutation.CREATE;
      case AccountDeclarationDecision.Reactivated _ -> AttestationEffectMutation.REACTIVATE;
      case AccountDeclarationDecision.Renamed _ -> AttestationEffectMutation.AMEND;
      case AccountDeclarationDecision.Unchanged _, AccountDeclarationDecision.Rejected _ ->
          throw new IllegalArgumentException(
              "Only a mutating account declaration has an attested effect.");
    };
  }
}
