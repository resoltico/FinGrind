package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Exercises lifecycle evidence admission before SQLite signs or persists the next operation. */
class AttestationLifecycleAdmissionCoverageTest {
  private static final UUID BOOK_ID = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
  private static final Instant RECORDED_AT = Instant.parse("2026-07-21T12:00:00Z");

  @Test
  void backupAcknowledgement_admissionDistinguishesAppendReplayAndConflict() {
    AttestationBackupAcknowledgement requested = acknowledgement(17, 1, 2);
    AttestationEvidence matching = backupEvidence(requested);
    AttestationEvidence unrelated = evidence(AttestationOperationKind.POST_ENTRY, emptyPreimage());

    assertEquals(
        AttestationBackupAcknowledgementAdmission.APPEND,
        AttestationBackupAcknowledgementAdmission.evaluate(List.of(), requested));
    assertEquals(
        AttestationBackupAcknowledgementAdmission.APPEND,
        AttestationBackupAcknowledgementAdmission.evaluate(List.of(unrelated), requested));
    assertEquals(
        AttestationBackupAcknowledgementAdmission.IDENTICAL_REPLAY,
        AttestationBackupAcknowledgementAdmission.evaluate(List.of(matching), requested));
    assertEquals(
        AttestationBackupAcknowledgementAdmission.APPEND,
        AttestationBackupAcknowledgementAdmission.evaluate(
            List.of(backupEvidence(otherBackupIdentity())), requested));
    assertEquals(
        AttestationBackupAcknowledgementAdmission.CONFLICT,
        AttestationBackupAcknowledgementAdmission.evaluate(
            List.of(matching, backupEvidence(acknowledgement(18, 3, 4))), requested));
  }

  @Test
  void backupAcknowledgement_ownsImmutableTupleAndRejectsMalformedValues() {
    byte[] digest = AttestationHash.sha256(new byte[] {1}).bytes();
    byte[] head = AttestationHash.sha256(new byte[] {2}).bytes();
    AttestationBackupAcknowledgement acknowledgement =
        new AttestationBackupAcknowledgement(UUID.randomUUID(), digest, BigInteger.ZERO, head);
    digest[0] ^= 1;
    head[0] ^= 1;

    assertFalse(java.util.Arrays.equals(digest, acknowledgement.backupArtifactDigest()));
    assertFalse(java.util.Arrays.equals(head, acknowledgement.sourceOperationHead()));
    assertTrue(acknowledgement.sameTuple(acknowledgement));
    assertFalse(acknowledgement.sameTuple(acknowledgement(1, 1, 2)));
    assertFalse(
        acknowledgement.sameTuple(
            new AttestationBackupAcknowledgement(
                acknowledgement.backupId(),
                AttestationHash.sha256(new byte[] {7}).bytes(),
                BigInteger.ZERO,
                acknowledgement.sourceOperationHead())));
    assertFalse(
        acknowledgement.sameTuple(
            new AttestationBackupAcknowledgement(
                acknowledgement.backupId(),
                acknowledgement.backupArtifactDigest(),
                BigInteger.ZERO,
                AttestationHash.sha256(new byte[] {8}).bytes())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationBackupAcknowledgement(
                UUID.randomUUID(), new byte[31], BigInteger.ZERO, new byte[32]));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationBackupAcknowledgement(
                UUID.randomUUID(), new byte[32], BigInteger.valueOf(-1), new byte[32]));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationBackupAcknowledgement(
                UUID.randomUUID(), new byte[32], BigInteger.ONE.shiftLeft(64), new byte[32]));
  }

  @Test
  void backupAcknowledgement_rejectsMalformedBackupEffectEvidence() {
    AttestationEvidence malformed =
        evidence(AttestationOperationKind.BACKUP_CREATED, emptyPreimage());

    assertThrows(
        AttestationAuthorizationException.class,
        () -> AttestationBackupAcknowledgementAdmission.from(malformed));
    assertThrows(
        NullPointerException.class, () -> AttestationBackupAcknowledgementAdmission.from(nullOf()));
    assertEquals(
        acknowledgement(17, 1, 2).backupId(),
        java.util.Objects.requireNonNull(
                AttestationBackupAcknowledgementAdmission.from(mixedBackupEvidence()))
            .backupId());
  }

  @Test
  void lifecycleState_derivesOnlyStrictlySequentialRekeyEpochs() {
    AttestationEvidence rekeyOne = rekeyEvidence(BigInteger.ONE);
    AttestationEvidence rekeyTwo = rekeyEvidence(BigInteger.TWO);

    assertEquals(BigInteger.ONE, AttestationLifecycleState.nextKeyEpoch(List.of()));
    assertEquals(BigInteger.TWO, AttestationLifecycleState.nextKeyEpoch(List.of(rekeyOne)));
    assertEquals(
        BigInteger.TWO, AttestationLifecycleState.nextKeyEpoch(List.of(mixedRekeyEvidence())));
    assertEquals(
        BigInteger.valueOf(3),
        AttestationLifecycleState.nextKeyEpoch(
            List.of(
                evidence(AttestationOperationKind.POST_ENTRY, emptyPreimage()),
                rekeyOne,
                rekeyTwo)));
    assertThrows(
        AttestationVerificationException.class,
        () -> AttestationLifecycleState.nextKeyEpoch(List.of(rekeyTwo)));
    assertThrows(
        AttestationVerificationException.class,
        () ->
            AttestationLifecycleState.nextKeyEpoch(
                List.of(evidence(AttestationOperationKind.REKEY_BOOK, emptyPreimage()))));
    assertThrows(
        NullPointerException.class,
        () -> AttestationLifecycleState.nextKeyEpoch(List.of(nullOf())));
  }

  @Test
  void verificationValueObjects_preserveImmutablePublicResults() {
    byte[] head = AttestationHash.sha256(new byte[] {9}).bytes();
    AttestationReviewFinding finding =
        new AttestationReviewFinding(
            new AttestationCompromiseReview(
                AttestationHash.sha256(new byte[] {8}).hex(), BigInteger.ZERO, null),
            BigInteger.ONE);
    AttestationVerification verification =
        new AttestationVerification(BOOK_ID, BigInteger.ONE, head, List.of(finding));
    AttestationBackupArtifactVerification backup =
        new AttestationBackupArtifactVerification(
            new byte[] {1, 2},
            BOOK_ID,
            UUID.randomUUID(),
            BigInteger.ONE,
            head,
            AttestationHash.sha256(new byte[] {1, 2}).bytes(),
            AttestationHash.sha256(new byte[] {3}).bytes(),
            verification);

    head[0] ^= 1;
    assertTrue(verification.reviewRequired());
    assertEquals(List.of(finding), verification.reviewFindings());
    assertArrayEquals(verification.operationHead(), backup.sourceOperationHead());
    assertArrayEquals(new byte[] {1, 2}, backup.snapshot());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationVerification(BOOK_ID, BigInteger.valueOf(-1), new byte[32], List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationVerification(BOOK_ID, BigInteger.ZERO, new byte[31], List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationVerification(
                BOOK_ID, BigInteger.ONE.shiftLeft(64), new byte[32], List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationBackupArtifactVerification(
                new byte[0],
                BOOK_ID,
                UUID.randomUUID(),
                BigInteger.ZERO,
                new byte[31],
                new byte[32],
                new byte[32],
                verification));
    assertEquals(backup.backupId(), backup.backupId());
    assertEquals(verification, backup.sourceVerification());
    assertEquals(AttestationHash.BYTE_LENGTH, backup.snapshotDigest().length);
    assertEquals(AttestationHash.BYTE_LENGTH, backup.artifactDigest().length);
    AttestationReceiptVerificationResult receipt =
        new AttestationReceiptVerificationResult(
            BOOK_ID, BigInteger.ONE, verification.operationHead(), List.of());
    assertEquals(BOOK_ID, receipt.bookId());
    assertEquals(BigInteger.ONE, receipt.operationOrder());
    assertArrayEquals(verification.operationHead(), receipt.operationHead());
    assertEquals(List.of(), receipt.findings());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationReceiptVerificationResult(
                BOOK_ID, BigInteger.ZERO, new byte[31], List.of()));
  }

  private static AttestationBackupAcknowledgement acknowledgement(
      int order, int digestSeed, int headSeed) {
    return new AttestationBackupAcknowledgement(
        UUID.fromString("4527c01b-654b-499c-88d7-dc1a14969215"),
        AttestationHash.sha256(new byte[] {(byte) digestSeed}).bytes(),
        BigInteger.valueOf(order),
        AttestationHash.sha256(new byte[] {(byte) headSeed}).bytes());
  }

  private static AttestationBackupAcknowledgement otherBackupIdentity() {
    AttestationBackupAcknowledgement baseline = acknowledgement(17, 1, 2);
    return new AttestationBackupAcknowledgement(
        UUID.fromString("bf27c01b-654b-499c-88d7-dc1a14969215"),
        baseline.backupArtifactDigest(),
        baseline.sourceOrder(),
        baseline.sourceOperationHead());
  }

  private static AttestationEvidence backupEvidence(
      AttestationBackupAcknowledgement acknowledgement) {
    AttestationOperationPreimages preimages =
        AttestationLifecycleMutationProjection.backupBook(
            AttestationOperationKind.BACKUP_CREATED.wireToken(), acknowledgement);
    return evidence(AttestationOperationKind.BACKUP_CREATED, preimages.effect());
  }

  private static AttestationEvidence rekeyEvidence(BigInteger epoch) {
    return evidence(
        AttestationOperationKind.REKEY_BOOK,
        AttestationLifecycleMutationProjection.rekeyBook(
                AttestationOperationKind.REKEY_BOOK.wireToken(),
                epoch,
                RECORDED_AT,
                java.util.Optional.empty())
            .effect());
  }

  private static AttestationEvidence mixedBackupEvidence() {
    AttestationPreimage backup =
        AttestationPreimage.decode(
            AttestationLifecycleMutationProjection.backupBook(
                    AttestationOperationKind.BACKUP_CREATED.wireToken(), acknowledgement(17, 1, 2))
                .effect(),
            AttestationAuthorizationFailure.PREIMAGE_INVALID);
    AttestationPreimage rekey =
        AttestationPreimage.decode(
            AttestationLifecycleMutationProjection.rekeyBook(
                    AttestationOperationKind.REKEY_BOOK.wireToken(),
                    BigInteger.ONE,
                    RECORDED_AT,
                    java.util.Optional.empty())
                .effect(),
            AttestationAuthorizationFailure.PREIMAGE_INVALID);
    return evidence(
        AttestationOperationKind.BACKUP_CREATED,
        AttestationPreimage.of(
                java.util.stream.Stream.concat(backup.records().stream(), rekey.records().stream())
                    .toList())
            .encoded());
  }

  private static AttestationEvidence mixedRekeyEvidence() {
    AttestationEvidence mixedBackup = mixedBackupEvidence();
    return evidence(AttestationOperationKind.REKEY_BOOK, mixedBackup.effectPreimage());
  }

  private static AttestationPreimage emptyPreimage() {
    return AttestationPreimage.of(List.of());
  }

  private static AttestationEvidence evidence(
      AttestationOperationKind operationKind, AttestationPreimage effectPreimage) {
    return evidence(operationKind, effectPreimage.encoded());
  }

  private static AttestationEvidence evidence(
      AttestationOperationKind operationKind, byte[] effectPreimage) {
    AttestationPreimage request = AttestationPreimage.of(List.of());
    AttestationOperationPayload payload =
        new AttestationOperationPayload(
            BOOK_ID,
            BigInteger.ONE,
            operationKind.wireToken(),
            AttestationHash.sha256(new byte[] {1}),
            RECORDED_AT,
            AttestationHash.sha256(request.encoded()),
            AttestationHash.sha256(effectPreimage));
    return new AttestationEvidence(
        AttestationEnvelope.of(payload, List.of()).encoded(), request.encoded(), effectPreimage);
  }
}
