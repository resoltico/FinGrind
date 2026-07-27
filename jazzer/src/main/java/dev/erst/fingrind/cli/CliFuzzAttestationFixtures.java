package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Synthetic attestation read models used only where a fixture needs an opened-book outcome. */
final class CliFuzzAttestationFixtures {
  private static final UUID SYNTHETIC_BOOK_ID =
      UUID.fromString("20314253-6475-8697-a8b9-cadbecfd0e1f");

  private CliFuzzAttestationFixtures() {}

  /** Returns an empty, structurally valid registry inspection for synthetic outcome fixtures. */
  static AttestationRegistryInspection syntheticTrustRoot() {
    return new AttestationRegistryInspection(
        SYNTHETIC_BOOK_ID,
        BigInteger.ZERO,
        "0".repeat(64),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  /** Returns the commitment identifying {@link #syntheticTrustRoot()}. */
  static AttestationCommit syntheticTrustRootCommitment() {
    AttestationRegistryInspection trustRoot = syntheticTrustRoot();
    return commitmentFor(trustRoot);
  }

  /** Completes an in-memory opening outcome before it crosses the published-result boundary. */
  static BookOpeningOutcome completeOpeningOutcome(BookOpeningOutcome outcome) {
    return switch (java.util.Objects.requireNonNull(outcome, "outcome")) {
      case BookOpeningOutcome.Opened opened ->
          new BookOpeningOutcome.Opened(
              opened.initializedAt(),
              opened.bookIdentity(),
              opened.attestationTrustRoot(),
              commitmentFor(opened.attestationTrustRoot()));
      case BookOpeningOutcome.Rejected rejected -> rejected;
    };
  }

  /**
   * Completes changed in-memory account outcomes before they cross the published-result boundary.
   */
  static AccountDeclarationOutcome completeAccountDeclarationOutcome(
      AccountDeclarationOutcome outcome) {
    return switch (java.util.Objects.requireNonNull(outcome, "outcome")) {
      case AccountDeclarationOutcome.Declared declared ->
          new AccountDeclarationOutcome.Declared(
              declared.account(), syntheticAppend());
      case AccountDeclarationOutcome.Reactivated reactivated ->
          new AccountDeclarationOutcome.Reactivated(
              reactivated.account(), syntheticAppend());
      case AccountDeclarationOutcome.Renamed renamed ->
          new AccountDeclarationOutcome.Renamed(renamed.account(), syntheticAppend());
      case AccountDeclarationOutcome.Unchanged unchanged -> unchanged;
      case AccountDeclarationOutcome.Rejected rejected -> rejected;
    };
  }

  private static AttestationCommit commitmentFor(AttestationRegistryInspection trustRoot) {
    return new AttestationCommit(trustRoot.headOrder(), trustRoot.operationHeadHex());
  }

  /** Returns a structurally valid synthetic append outcome for changed account fixtures. */
  static AttestationAppendOutcome.Appended syntheticAppend() {
    return new AttestationAppendOutcome.Appended(
        new AttestationVerification(
            SYNTHETIC_BOOK_ID,
            BigInteger.ONE,
            HexFormat.of().parseHex("a".repeat(64)),
            new byte[32],
            List.of()));
  }
}
