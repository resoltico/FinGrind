package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves recovery evidence names the exact independently verified lifecycle operation. */
class AttestationLifecycleRecoveryEvidenceVerifierTest {
  @Test
  void restoreHeadMatchesOnlyTheAcknowledgedRestoreOperation() {
    List<AttestationEvidence> evidence = evidence("B-06");
    AttestationBookOperation restoredHead = operation("B-06");
    AttestationBackupAcknowledgement acknowledgement = acknowledgement();

    assertTrue(
        AttestationLifecycleRecoveryEvidenceVerifier.matchesRestoreHead(
            evidence,
            acknowledgement,
            restoredHead.envelope().payload().operationOrder(),
            restoredHead.envelope().head().bytes()));
    assertFalse(
        AttestationLifecycleRecoveryEvidenceVerifier.matchesRestoreHead(
            evidence,
            acknowledgement,
            restoredHead.envelope().payload().operationOrder(),
            new byte[32]));
    assertFalse(
        AttestationLifecycleRecoveryEvidenceVerifier.matchesRestoreHead(
            evidence,
            acknowledgement,
            restoredHead.envelope().payload().operationOrder(),
            new byte[31]));
  }

  @Test
  void restoreHeadRejectsAValidChainThatDoesNotCarryTheNamedAcknowledgement() {
    List<AttestationEvidence> evidence = evidence("B-06");
    AttestationBookOperation restoredHead = operation("B-06");
    AttestationBookOperation rekeyHead = operation("B-10");
    AttestationBackupAcknowledgement original = acknowledgement();
    AttestationBackupAcknowledgement otherAcknowledgement =
        new AttestationBackupAcknowledgement(
            UUID.fromString("b4eea19f-9b38-4a29-91af-a9dad184d202"),
            original.backupArtifactDigest(),
            original.sourceOrder(),
            original.sourceOperationHead());

    assertFalse(
        AttestationLifecycleRecoveryEvidenceVerifier.matchesRestoreHead(
            evidence,
            otherAcknowledgement,
            restoredHead.envelope().payload().operationOrder(),
            restoredHead.envelope().head().bytes()));
    assertFalse(
        AttestationLifecycleRecoveryEvidenceVerifier.matchesRestoreHead(
            List.of(),
            original,
            restoredHead.envelope().payload().operationOrder(),
            restoredHead.envelope().head().bytes()));
    assertFalse(
        AttestationLifecycleRecoveryEvidenceVerifier.matchesRestoreHead(
            evidence("B-10"),
            original,
            rekeyHead.envelope().payload().operationOrder(),
            rekeyHead.envelope().head().bytes()));
    assertFalse(
        AttestationLifecycleRecoveryEvidenceVerifier.matchesRestoreHead(
            evidence, original, BigInteger.valueOf(3), restoredHead.envelope().head().bytes()));
  }

  @Test
  void rekeyHeadMatchesOnlyAnExactVerifiedRekeyHead() {
    List<AttestationEvidence> evidence = evidence("B-10");
    AttestationBookOperation rekeyHead = operation("B-10");

    assertTrue(
        AttestationLifecycleRecoveryEvidenceVerifier.matchesRekeyHead(
            evidence,
            rekeyHead.envelope().payload().operationOrder(),
            rekeyHead.envelope().head().bytes()));
    assertFalse(
        AttestationLifecycleRecoveryEvidenceVerifier.matchesRekeyHead(
            evidence, BigInteger.ZERO, rekeyHead.envelope().head().bytes()));
  }

  @Test
  void rekeyHeadRejectsAnExpectedHeadWithTheWrongOperationKindOrHashLength() {
    AttestationBookOperation restoreHead = operation("B-06");

    assertFalse(
        AttestationLifecycleRecoveryEvidenceVerifier.matchesRekeyHead(
            evidence("B-06"),
            restoreHead.envelope().payload().operationOrder(),
            restoreHead.envelope().head().bytes()));
    assertFalse(
        AttestationLifecycleRecoveryEvidenceVerifier.matchesRekeyHead(
            evidence("B-10"),
            operation("B-10").envelope().payload().operationOrder(),
            new byte[31]));
  }

  private static AttestationBackupAcknowledgement acknowledgement() {
    AttestationBookOperation sourceHead = operation("B-02");
    return new AttestationBackupAcknowledgement(
        UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100"),
        AttestationHash.sha256(AttestationStaticCorpusVectors.source("B-05-artifact")).bytes(),
        sourceHead.envelope().payload().operationOrder(),
        sourceHead.envelope().head().bytes());
  }

  private static List<AttestationEvidence> evidence(String bookId) {
    return AttestationStaticCorpusVectors.book(bookId).operations().stream()
        .map(
            operation ->
                new AttestationEvidence(
                    operation.envelope().encoded(),
                    operation.requestPreimage().encoded(),
                    operation.effectPreimage().encoded()))
        .toList();
  }

  private static AttestationBookOperation operation(String bookId) {
    return AttestationStaticCorpusVectors.book(bookId).operations().getLast();
  }
}
