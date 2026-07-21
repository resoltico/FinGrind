package dev.erst.fingrind.executor.maintenance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.attestation.AttestationVerification;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers the exact live-chain address and source-state binding required for backup resumption. */
class AttestedProtectedBookBackupAcknowledgementWorkflowTest {
  private static final UUID BOOK_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

  @Test
  void acceptsOnlyAddressableSourceOrdersAndExactManifestSourceBindings() {
    AttestationVerification verification =
        new AttestationVerification(BOOK_ID, BigInteger.ZERO, new byte[32], List.of());

    assertTrue(
        AttestedProtectedBookBackupAcknowledgementWorkflow.sourceOrderIsAddressable(
            BigInteger.ZERO, 1));
    assertFalse(
        AttestedProtectedBookBackupAcknowledgementWorkflow.sourceOrderIsAddressable(
            BigInteger.ZERO, 0));
    assertFalse(
        AttestedProtectedBookBackupAcknowledgementWorkflow.sourceOrderIsAddressable(
            BigInteger.valueOf(Long.MAX_VALUE), 1));

    assertTrue(
        AttestedProtectedBookBackupAcknowledgementWorkflow.sourceVerificationMatchesArtifact(
            verification, BOOK_ID, BigInteger.ZERO, new byte[32]));
    assertFalse(
        AttestedProtectedBookBackupAcknowledgementWorkflow.sourceVerificationMatchesArtifact(
            verification,
            UUID.fromString("018f0000-0000-7000-8000-000000000002"),
            BigInteger.ZERO,
            new byte[32]));
    assertFalse(
        AttestedProtectedBookBackupAcknowledgementWorkflow.sourceVerificationMatchesArtifact(
            verification, BOOK_ID, BigInteger.ONE, new byte[32]));
    byte[] differentHead = new byte[32];
    differentHead[0] = 1;
    assertFalse(
        AttestedProtectedBookBackupAcknowledgementWorkflow.sourceVerificationMatchesArtifact(
            verification, BOOK_ID, BigInteger.ZERO, differentHead));
  }
}
