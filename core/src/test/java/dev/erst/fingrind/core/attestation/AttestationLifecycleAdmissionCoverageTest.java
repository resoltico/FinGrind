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
    assertThrows(
        NullPointerException.class,
        () -> AttestationBackupAcknowledgementAdmission.evaluate(nullOf(), requested));
    assertThrows(
        NullPointerException.class,
        () -> AttestationBackupAcknowledgementAdmission.evaluate(List.of(), nullOf()));
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
    assertThrows(
        AttestationAuthorizationException.class,
        () -> AttestationBackupAcknowledgementAdmission.from(mixedBackupEvidence()));
  }

  @Test
  void lifecycleState_derivesOnlyFromACompleteVerifiedRekeyChain() {
    AttestationEvidence rekeyTwo = rekeyEvidence(BigInteger.TWO);
    AttestationEvidence rekeyThree = rekeyEvidence(BigInteger.valueOf(3));
    AttestationCorpusResources.Book rekeyFixture = AttestationStaticCorpusVectors.book("B-10");
    List<AttestationEvidence> verifiedRekeyEvidence =
        rekeyFixture.operations().stream()
            .map(
                operation ->
                    new AttestationEvidence(
                        operation.envelope().encoded(),
                        operation.requestPreimage().encoded(),
                        operation.effectPreimage().encoded()))
            .toList();

    assertEquals(BigInteger.TWO, AttestationLifecycleState.nextKeyEpoch(List.of()));
    assertEquals(
        BigInteger.valueOf(3), AttestationLifecycleState.nextKeyEpoch(verifiedRekeyEvidence));
    assertThrows(
        AttestationVerificationException.class,
        () -> AttestationLifecycleState.nextKeyEpoch(List.of(rekeyTwo)));
    assertThrows(
        AttestationVerificationException.class,
        () -> AttestationLifecycleState.nextKeyEpoch(List.of(rekeyThree)));
    assertThrows(
        AttestationVerificationException.class,
        () -> AttestationLifecycleState.nextKeyEpoch(List.of(mixedRekeyEvidence())));
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
    byte[] previousHead = AttestationHash.sha256(new byte[] {10}).bytes();
    AttestationVerification verification =
        new AttestationVerification(BOOK_ID, BigInteger.ONE, head, previousHead, List.of(finding));
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
    previousHead[0] ^= 1;
    assertTrue(verification.reviewRequired());
    assertEquals(List.of(finding), verification.reviewFindings());
    assertArrayEquals(verification.operationHead(), backup.sourceOperationHead());
    assertFalse(java.util.Arrays.equals(previousHead, verification.previousHead()));
    assertArrayEquals(new byte[] {1, 2}, backup.snapshot());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationVerification(
                BOOK_ID, BigInteger.valueOf(-1), new byte[32], new byte[32], List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationVerification(
                BOOK_ID, BigInteger.ZERO, new byte[31], new byte[32], List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationVerification(
                BOOK_ID, BigInteger.ZERO, new byte[32], new byte[31], List.of()));
    byte[] nonGenesisPredecessor = new byte[32];
    nonGenesisPredecessor[0] = 1;
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationVerification(
                BOOK_ID, BigInteger.ZERO, new byte[32], nonGenesisPredecessor, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationVerification(
                BOOK_ID, BigInteger.ONE.shiftLeft(64), new byte[32], new byte[32], List.of()));
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

  @Test
  void verificationResults_rejectFindingsOutsideTheVerifiedReviewScope() {
    AttestationCompromiseReview unboundedReview =
        new AttestationCompromiseReview(
            AttestationHash.sha256(new byte[] {7}).hex(), BigInteger.ZERO, null);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationVerification(
                BOOK_ID,
                BigInteger.ONE,
                new byte[32],
                new byte[32],
                List.of(new AttestationReviewFinding(unboundedReview, BigInteger.TWO))));

    AttestationCompromiseReview boundedReview =
        new AttestationCompromiseReview(
            AttestationHash.sha256(new byte[] {8}).hex(), BigInteger.ONE, BigInteger.ONE);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationVerification(
                BOOK_ID,
                BigInteger.TWO,
                new byte[32],
                new byte[32],
                List.of(new AttestationReviewFinding(boundedReview, BigInteger.ZERO))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationVerification(
                BOOK_ID,
                BigInteger.valueOf(3),
                new byte[32],
                new byte[32],
                List.of(new AttestationReviewFinding(boundedReview, BigInteger.valueOf(3)))));

    AttestationReviewFinding first = new AttestationReviewFinding(boundedReview, BigInteger.ONE);
    AttestationReviewFinding duplicate =
        new AttestationReviewFinding(
            new AttestationCompromiseReview(
                AttestationHash.sha256(new byte[] {8}).hex(), BigInteger.ONE, BigInteger.ONE),
            BigInteger.ONE);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationVerification(
                BOOK_ID, BigInteger.TWO, new byte[32], new byte[32], List.of(first, duplicate)));
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
    return evidence(
        AttestationOperationKind.BACKUP_CREATED, preimages.request(), preimages.effect());
  }

  private static AttestationEvidence rekeyEvidence(BigInteger epoch) {
    AttestationOperationPreimages preimages =
        AttestationLifecycleMutationProjection.rekeyBook(
            AttestationOperationKind.REKEY_BOOK.wireToken(),
            epoch,
            RECORDED_AT,
            java.util.Optional.empty());
    return evidence(AttestationOperationKind.REKEY_BOOK, preimages.request(), preimages.effect());
  }

  private static AttestationEvidence mixedBackupEvidence() {
    AttestationOperationPreimages backupPreimages =
        AttestationLifecycleMutationProjection.backupBook(
            AttestationOperationKind.BACKUP_CREATED.wireToken(), acknowledgement(17, 1, 2));
    AttestationPreimage backup =
        AttestationPreimage.decode(
            backupPreimages.effect(), AttestationAuthorizationFailure.PREIMAGE_INVALID);
    AttestationPreimage rekey =
        AttestationPreimage.decode(
            AttestationLifecycleMutationProjection.rekeyBook(
                    AttestationOperationKind.REKEY_BOOK.wireToken(),
                    BigInteger.TWO,
                    RECORDED_AT,
                    java.util.Optional.empty())
                .effect(),
            AttestationAuthorizationFailure.PREIMAGE_INVALID);
    return evidence(
        AttestationOperationKind.BACKUP_CREATED,
        backupPreimages.request(),
        AttestationPreimage.of(
                java.util.stream.Stream.concat(backup.records().stream(), rekey.records().stream())
                    .toList())
            .encoded());
  }

  private static AttestationEvidence mixedRekeyEvidence() {
    AttestationEvidence mixedBackup = mixedBackupEvidence();
    AttestationOperationPreimages rekey =
        AttestationLifecycleMutationProjection.rekeyBook(
            AttestationOperationKind.REKEY_BOOK.wireToken(),
            BigInteger.TWO,
            RECORDED_AT,
            java.util.Optional.empty());
    return evidence(
        AttestationOperationKind.REKEY_BOOK, rekey.request(), mixedBackup.effectPreimage());
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
    return evidence(operationKind, AttestationPreimage.of(List.of()).encoded(), effectPreimage);
  }

  private static AttestationEvidence evidence(
      AttestationOperationKind operationKind, byte[] requestPreimage, byte[] effectPreimage) {
    AttestationOperationPayload payload =
        new AttestationOperationPayload(
            BOOK_ID,
            BigInteger.ONE,
            operationKind.wireToken(),
            AttestationHash.sha256(new byte[] {1}),
            RECORDED_AT,
            AttestationHash.sha256(requestPreimage),
            AttestationHash.sha256(effectPreimage));
    return new AttestationEvidence(
        AttestationEnvelope.of(payload, List.of()).encoded(), requestPreimage, effectPreimage);
  }
}
