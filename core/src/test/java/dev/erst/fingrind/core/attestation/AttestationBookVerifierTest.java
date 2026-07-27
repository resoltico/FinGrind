package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisContext;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisEffectPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisPayload;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisRequestPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.signedGenesisEnvelope;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Exercises the immutable protected-book chain walk, not a caller-supplied registry shortcut. */
class AttestationBookVerifierTest {
  @Test
  void walksGenesisAndAValidSuccessorAndReportsInclusiveCompromiseReview() {
    TestCredential founder = credential();
    AttestationBookOperation genesis = genesis(founder);
    AttestationBookOperation successor = successor(founder, genesis.envelope().head());

    AttestationBookVerification verification =
        AttestationBookVerifier.verify(
            new AttestationBook(List.of(genesis, successor)),
            List.of(new AttestationCompromiseReview(founder.keyId().hex(), BigInteger.ONE, null)));

    assertEquals(BigInteger.ONE, verification.headOrder());
    assertEquals(successor.envelope().head(), verification.head());
    assertEquals(
        List.of(
            new AttestationReviewFinding(
                new AttestationCompromiseReview(founder.keyId().hex(), BigInteger.ONE, null),
                BigInteger.ONE)),
        verification.reviewFindings());
  }

  @Test
  void canonicalizesNonOverlappingReviewsAndRejectsMalformedOrOverlappingIntervals() {
    String firstKeyId = "a".repeat(64);
    String secondKeyId = "b".repeat(64);
    AttestationCompromiseReview first =
        new AttestationCompromiseReview(firstKeyId, BigInteger.ZERO, BigInteger.ZERO);
    AttestationCompromiseReview second =
        new AttestationCompromiseReview(firstKeyId, BigInteger.ONE, BigInteger.ONE);
    AttestationCompromiseReview otherCredential =
        new AttestationCompromiseReview(secondKeyId, BigInteger.TWO, BigInteger.TWO);

    assertEquals(
        List.of(first, second, otherCredential),
        AttestationCompromiseReview.canonicalize(List.of(otherCredential, second, first)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationCompromiseReview("A".repeat(64), BigInteger.ZERO, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationCompromiseReview.canonicalize(
                List.of(
                    new AttestationCompromiseReview(firstKeyId, BigInteger.ZERO, BigInteger.ONE),
                    new AttestationCompromiseReview(firstKeyId, BigInteger.ONE, BigInteger.TWO))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationCompromiseReview.canonicalize(
                List.of(
                    new AttestationCompromiseReview(firstKeyId, BigInteger.ZERO, null),
                    new AttestationCompromiseReview(firstKeyId, BigInteger.TWO, null))));
  }

  @Test
  void reviewDeclarationsMustFitTheAuthenticatedHeadBeforeFindingsAreDerived() {
    TestCredential founder = credential();
    AttestationBookOperation genesis = genesis(founder);
    AttestationCompromiseReview boundedPastHead =
        new AttestationCompromiseReview(founder.keyId().hex(), BigInteger.ZERO, BigInteger.ONE);

    AttestationReviewWindowException boundedFailure =
        assertThrows(
            AttestationReviewWindowException.class,
            () ->
                AttestationBookVerifier.verify(
                    new AttestationBook(List.of(genesis)), List.of(boundedPastHead)));
    assertEquals(boundedPastHead, boundedFailure.review());
    assertEquals(BigInteger.ZERO, boundedFailure.verifiedHeadOrder());

    AttestationCompromiseReview openPastHead =
        new AttestationCompromiseReview(founder.keyId().hex(), BigInteger.ONE, null);
    AttestationReviewWindowException openFailure =
        assertThrows(
            AttestationReviewWindowException.class,
            () ->
                AttestationBookVerifier.verify(
                    new AttestationBook(List.of(genesis)), List.of(openPastHead)));
    assertEquals(openPastHead, openFailure.review());
    assertEquals(BigInteger.ZERO, openFailure.verifiedHeadOrder());

    AttestationCompromiseReview throughHead =
        new AttestationCompromiseReview(founder.keyId().hex(), BigInteger.ZERO, null);
    assertEquals(
        List.of(new AttestationReviewFinding(throughHead, BigInteger.ZERO)),
        AttestationBookVerifier.verify(new AttestationBook(List.of(genesis)), List.of(throughHead))
            .reviewFindings());
  }

  @Test
  void reportsTheChainFailureBeforeTheInvalidSignatureThatDependsOnItsPayload() {
    TestCredential founder = credential();
    AttestationBookOperation genesis = genesis(founder);
    AttestationBookOperation successor =
        successor(founder, AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]));

    assertFailure(
        AttestationAuthorizationFailure.PREVIOUS_HEAD_INVALID,
        () -> AttestationBookVerifier.verify(new AttestationBook(List.of(genesis, successor))));
  }

  @Test
  void rejectsASignedOperationWhosePreimagesOmitItsClosedPostingProfile() {
    AttestationPreimage request =
        AttestationAuthorizationTestSupport.requestPreimage(
            AttestationOperationKind.POST_ENTRY, AttestationSourceChannel.CLI, null);
    AttestationPreimage effect = AttestationPreimage.of(List.of());
    AttestationOperationPayload payload =
        new AttestationOperationPayload(
            AttestationAuthorizationTestSupport.BOOK_ID,
            BigInteger.ONE,
            AttestationOperationKind.POST_ENTRY.wireToken(),
            AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]),
            Instant.parse("2026-07-20T00:00:00.001Z"),
            AttestationHash.sha256(request.encoded()),
            AttestationHash.sha256(effect.encoded()));

    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireValid(
                payload, AttestationOperationKind.POST_ENTRY, request, effect));
  }

  @Test
  void rejectsNonSequentialAndInternallyMismatchedRekeyFactsInTheAuthenticatedChain() {
    TestCredential founder = credential();
    AttestationBookOperation genesis = genesis(founder);
    AttestationBookOperation firstRekey =
        rekey(founder, BigInteger.ONE, genesis.envelope().head(), BigInteger.TWO, BigInteger.TWO);

    assertDoesNotThrow(
        () -> AttestationBookVerifier.verify(new AttestationBook(List.of(genesis, firstRekey))));

    AttestationBookOperation skippedEpoch =
        rekey(
            founder,
            BigInteger.ONE,
            genesis.envelope().head(),
            BigInteger.valueOf(3),
            BigInteger.valueOf(3));
    AttestationBookOperation duplicateEpoch =
        rekey(
            founder, BigInteger.TWO, firstRekey.envelope().head(), BigInteger.TWO, BigInteger.TWO);
    AttestationBookOperation mismatchedEpoch =
        rekey(
            founder,
            BigInteger.ONE,
            genesis.envelope().head(),
            BigInteger.TWO,
            BigInteger.valueOf(3));

    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () -> AttestationBookVerifier.verify(new AttestationBook(List.of(genesis, skippedEpoch))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationBookVerifier.verify(
                new AttestationBook(List.of(genesis, firstRekey, duplicateEpoch))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationBookVerifier.verify(new AttestationBook(List.of(genesis, mismatchedEpoch))));
  }

  @Test
  void rejectsSignedLifecycleFactsWithWrongVerbsTuplesOrHistoricalBindings() {
    TestCredential founder = credential();
    AttestationBookOperation genesis = genesis(founder);
    UUID backupId = UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100");
    AttestationHash artifactDigest = AttestationHash.sha256(new byte[] {2});
    AttestationPreimage backupRequest =
        backupRequest(backupId, artifactDigest, genesis.envelope().head());
    AttestationBookOperation acknowledgedBackup =
        signedOperation(
            founder,
            AttestationAuthorizationTestSupport.BOOK_ID,
            BigInteger.ONE,
            AttestationOperationKind.BACKUP_CREATED,
            genesis.envelope().head(),
            backupRequest,
            backupEffect(backupId, artifactDigest, genesis.envelope().head()),
            Instant.parse("2026-07-20T00:00:00.001Z"));
    AttestationBookOperation duplicateBackup =
        signedOperation(
            founder,
            AttestationAuthorizationTestSupport.BOOK_ID,
            BigInteger.TWO,
            AttestationOperationKind.BACKUP_CREATED,
            acknowledgedBackup.envelope().head(),
            backupRequest,
            backupEffect(backupId, artifactDigest, genesis.envelope().head()),
            Instant.parse("2026-07-20T00:00:00.002Z"));
    AttestationHash conflictingArtifactDigest = AttestationHash.sha256(new byte[] {3});
    AttestationBookOperation reusedBackupIdWithDifferentTuple =
        signedOperation(
            founder,
            AttestationAuthorizationTestSupport.BOOK_ID,
            BigInteger.TWO,
            AttestationOperationKind.BACKUP_CREATED,
            acknowledgedBackup.envelope().head(),
            backupRequest(backupId, conflictingArtifactDigest, genesis.envelope().head()),
            backupEffect(backupId, conflictingArtifactDigest, genesis.envelope().head()),
            Instant.parse("2026-07-20T00:00:00.002Z"));
    AttestationBookOperation wrongBackupVerb =
        signedOperation(
            founder,
            AttestationAuthorizationTestSupport.BOOK_ID,
            BigInteger.ONE,
            AttestationOperationKind.BACKUP_CREATED,
            genesis.envelope().head(),
            backupRequest,
            backupEffect(
                backupId,
                artifactDigest,
                genesis.envelope().head(),
                AttestationEffectMutation.CREATE),
            Instant.parse("2026-07-20T00:00:00.001Z"));
    AttestationBookOperation mismatchedBackupTuple =
        signedOperation(
            founder,
            AttestationAuthorizationTestSupport.BOOK_ID,
            BigInteger.ONE,
            AttestationOperationKind.BACKUP_CREATED,
            genesis.envelope().head(),
            backupRequest,
            backupEffect(
                UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221101"),
                artifactDigest,
                genesis.envelope().head()),
            Instant.parse("2026-07-20T00:00:00.001Z"));
    AttestationBackupAcknowledgement invalidRestoreSource =
        new AttestationBackupAcknowledgement(
            backupId,
            artifactDigest.bytes(),
            BigInteger.ZERO,
            AttestationHash.sha256(new byte[] {9}).bytes());
    AttestationOperationPreimages restorePreimages =
        AttestationLifecycleMutationProjection.restoreBook(
            AttestationOperationKind.RESTORE_BOOK.wireToken(), invalidRestoreSource);
    AttestationBookOperation invalidRestorePredecessor =
        signedOperation(
            founder,
            AttestationAuthorizationTestSupport.BOOK_ID,
            BigInteger.ONE,
            AttestationOperationKind.RESTORE_BOOK,
            genesis.envelope().head(),
            decodePreimage(restorePreimages.request()),
            decodePreimage(restorePreimages.effect()),
            Instant.parse("2026-07-20T00:00:00.001Z"));

    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationBookVerifier.verify(
                new AttestationBook(List.of(genesis, acknowledgedBackup, duplicateBackup))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationBookVerifier.verify(
                new AttestationBook(
                    List.of(genesis, acknowledgedBackup, reusedBackupIdWithDifferentTuple))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationBookVerifier.verify(new AttestationBook(List.of(genesis, wrongBackupVerb))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationBookVerifier.verify(
                new AttestationBook(List.of(genesis, mismatchedBackupTuple))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationBookVerifier.verify(
                new AttestationBook(List.of(genesis, invalidRestorePredecessor))));
  }

  @Test
  void rejectsASignedRekeyWhoseDerivedInstantDiffersFromItsOperationInstant() {
    TestCredential founder = credential();
    AttestationBookOperation genesis = genesis(founder);
    AttestationBookOperation mismatchedRecordedAt =
        rekey(
            founder,
            BigInteger.ONE,
            genesis.envelope().head(),
            BigInteger.TWO,
            BigInteger.TWO,
            Instant.parse("2026-07-20T00:00:00.001Z"),
            Instant.parse("2026-07-20T00:00:00.002Z"));

    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationBookVerifier.verify(
                new AttestationBook(List.of(genesis, mismatchedRecordedAt))));
  }

  @Test
  void rejectsEveryBrokenChainPositionBeforeAnyDependentAuthorization() {
    TestCredential founder = credential();
    AttestationBookOperation genesis = genesis(founder);
    AttestationBook missingGenesis =
        new AttestationBook(
            List.of(
                successor(
                    founder,
                    AttestationAuthorizationTestSupport.BOOK_ID,
                    BigInteger.ONE,
                    AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]))));
    AttestationBook wrongBookId =
        new AttestationBook(
            List.of(
                genesis,
                successor(
                    founder,
                    UUID.fromString("11000000-0000-0000-0000-000000000001"),
                    BigInteger.ONE,
                    genesis.envelope().head())));
    AttestationBook wrongOrder =
        new AttestationBook(
            List.of(
                genesis,
                successor(
                    founder,
                    AttestationAuthorizationTestSupport.BOOK_ID,
                    BigInteger.TWO,
                    genesis.envelope().head())));

    assertFailure(
        AttestationAuthorizationFailure.PREVIOUS_HEAD_INVALID,
        () -> AttestationBookVerifier.verify(new AttestationBook(List.of(genesis, genesis))));
    assertFailure(
        AttestationAuthorizationFailure.PREVIOUS_HEAD_INVALID,
        () -> AttestationBookVerifier.verify(missingGenesis));
    assertFailure(
        AttestationAuthorizationFailure.PREVIOUS_HEAD_INVALID,
        () -> AttestationBookVerifier.verify(wrongBookId));
    assertFailure(
        AttestationAuthorizationFailure.PREVIOUS_HEAD_INVALID,
        () -> AttestationBookVerifier.verify(wrongOrder));
  }

  @Test
  void rejectsAChainWhoseRawPreimageBytesDoNotMatchTheSignedDigest() {
    TestCredential founder = credential();
    AttestationBookOperation genesis = genesis(founder);
    AttestationBookOperation successor = successor(founder, genesis.envelope().head());
    AttestationBookOperation mismatched =
        AttestationBookOperation.decode(
            successor.envelope().encoded(),
            backupRequest(
                    UUID.fromString("11000000-0000-0000-0000-000000000001"),
                    AttestationHash.sha256(new byte[] {7}),
                    genesis.envelope().head())
                .encoded(),
            successor.effectPreimage().encoded());
    AttestationBookOperation effectMismatched =
        AttestationBookOperation.decode(
            successor.envelope().encoded(),
            successor.requestPreimage().encoded(),
            backupEffect(
                    UUID.fromString("11000000-0000-0000-0000-000000000002"),
                    AttestationHash.sha256(new byte[] {8}),
                    genesis.envelope().head())
                .encoded());

    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () -> AttestationBookVerifier.verify(new AttestationBook(List.of(genesis, mismatched))));
    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () ->
            AttestationBookVerifier.verify(
                new AttestationBook(List.of(genesis, effectMismatched))));
  }

  @Test
  void reportsMalformedPreimagesBeforeAnUnsupportedOperationPayloadAlgorithm() {
    TestCredential founder = credential();
    AttestationBookOperation genesis = genesis(founder);
    AttestationBookOperation successor = successor(founder, genesis.envelope().head());
    byte[] envelope = successor.envelope().encoded();
    envelope[operationAlgorithmValueOffset(envelope) + "ed25519".length() - 1] = '8';

    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () ->
            AttestationBookOperation.decode(
                envelope, new byte[0], successor.effectPreimage().encoded()));
  }

  @Test
  void rejectsAnUnsupportedOperationPayloadAlgorithmAfterItsPreimagesAreValid() {
    TestCredential founder = credential();
    AttestationBookOperation genesis = genesis(founder);
    AttestationBookOperation successor = successor(founder, genesis.envelope().head());
    byte[] envelope = successor.envelope().encoded();
    envelope[operationAlgorithmValueOffset(envelope) + "ed25519".length() - 1] = '8';
    AttestationBookOperation unsupportedAlgorithm =
        AttestationBookOperation.decode(
            envelope, successor.requestPreimage().encoded(), successor.effectPreimage().encoded());

    assertFailure(
        AttestationAuthorizationFailure.KEY_ALGORITHM_INVALID,
        () ->
            AttestationBookVerifier.verify(
                new AttestationBook(List.of(genesis, unsupportedAlgorithm))));
  }

  @Test
  void rejectsEveryBoundedAlternateLengthOperationAlgorithmAtTheSharedAlgorithmCheck() {
    assertAlternateLengthOperationAlgorithmIsRejectedAtTheSharedCheck("ed2551");
    assertAlternateLengthOperationAlgorithmIsRejectedAtTheSharedCheck("ed255190");
  }

  private static void assertAlternateLengthOperationAlgorithmIsRejectedAtTheSharedCheck(
      String algorithmId) {
    TestCredential founder = credential();
    AttestationBookOperation genesis = genesis(founder);
    AttestationBookOperation successor = successor(founder, genesis.envelope().head());
    byte[] envelope =
        replaceAlgorithmId(
            successor.envelope().encoded(),
            operationAlgorithmValueOffset(successor.envelope().encoded()) - 1,
            algorithmId);
    AttestationBookOperation unsupportedAlgorithm =
        AttestationBookOperation.decode(
            envelope, successor.requestPreimage().encoded(), successor.effectPreimage().encoded());

    assertFailure(
        AttestationAuthorizationFailure.KEY_ALGORITHM_INVALID,
        () ->
            AttestationBookVerifier.verify(
                new AttestationBook(List.of(genesis, unsupportedAlgorithm))));
  }

  @Test
  void keepsReviewIntervalsAndBookResultAccessorsInternallyConsistent() {
    TestCredential founder = credential();
    AttestationBookOperation genesis = genesis(founder);
    AttestationBookVerification verification =
        AttestationBookVerifier.verify(new AttestationBook(List.of(genesis)));
    AttestationCompromiseReview review =
        new AttestationCompromiseReview(founder.keyId().hex(), BigInteger.ONE, BigInteger.TWO);

    assertFalse(review.includes(BigInteger.ZERO));
    assertTrue(review.includes(BigInteger.ONE));
    assertTrue(review.includes(BigInteger.TWO));
    assertFalse(review.includes(BigInteger.valueOf(3)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationCompromiseReview(
                founder.keyId().hex(), BigInteger.ONE, BigInteger.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationBookVerification(
                verification.bookId(), List.of(), verification.registry(), List.of()));

    assertEquals(verification.bookId(), verification.bookId());
    assertEquals(verification.head(), verification.headAt(BigInteger.ZERO));
    assertEquals(genesis, verification.operationAt(BigInteger.ZERO).operation());
    assertEquals(verification.registry(), verification.registry());
    assertEquals(List.of(), verification.reviewFindings());
    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () -> verification.operationAt(BigInteger.ONE));
    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () -> verification.operationAt(BigInteger.ONE.negate()));
    AttestationBookVerification inconsistent =
        new AttestationBookVerification(
            verification.bookId(),
            List.of(
                new AttestationBookVerification.VerifiedOperation(
                    BigInteger.ONE, verification.head(), genesis)),
            verification.registry(),
            List.of());
    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () -> inconsistent.operationAt(BigInteger.ZERO));
  }

  @Test
  void rejectsAnEmptyCandidateBookThroughTheCanonicalMissingGenesisFailure() {
    assertFailure(
        AttestationAuthorizationFailure.PREVIOUS_HEAD_INVALID,
        () -> AttestationBookVerifier.verify(new AttestationBook(List.of())));
  }

  @Test
  void reportsOnlyReviewsThatCoverAnOperationSignedByTheNamedCredential() {
    TestCredential founder = credential();
    AttestationBook book = new AttestationBook(List.of(genesis(founder)));

    assertEquals(
        List.of(),
        AttestationBookVerifier.verify(
                book,
                List.of(
                    new AttestationCompromiseReview(
                        AttestationHash.sha256(new byte[] {9}).hex(),
                        BigInteger.ZERO,
                        BigInteger.ZERO)))
            .reviewFindings());
  }

  private static AttestationBookOperation genesis(TestCredential founder) {
    AttestationPreimage request = genesisRequestPreimage(founder);
    AttestationPreimage effect = genesisEffectPreimage(founder);
    AttestationOperationPayload payload =
        genesisPayload(
            BigInteger.ZERO,
            AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]),
            request,
            effect);
    AttestationGenesisAuthorizationContext context = genesisContext(founder);
    AttestationAuthorizationEnvelope envelope = signedGenesisEnvelope(context, founder);
    return AttestationBookOperation.decode(
        envelopeBytes(payload, envelope), request.encoded(), effect.encoded());
  }

  private static int operationAlgorithmValueOffset(byte[] envelope) {
    int operationKindLengthOffset = 8 + 1 + 16 + Long.BYTES;
    return operationKindLengthOffset
        + 1
        + Byte.toUnsignedInt(envelope[operationKindLengthOffset])
        + 1;
  }

  private static byte[] replaceAlgorithmId(
      byte[] encoded, int lengthOffset, String replacementAlgorithmId) {
    byte[] replacement = replacementAlgorithmId.getBytes(StandardCharsets.US_ASCII);
    int previousLength = Byte.toUnsignedInt(encoded[lengthOffset]);
    byte[] replaced = new byte[encoded.length - previousLength + replacement.length];
    System.arraycopy(encoded, 0, replaced, 0, lengthOffset);
    replaced[lengthOffset] = (byte) replacement.length;
    System.arraycopy(replacement, 0, replaced, lengthOffset + 1, replacement.length);
    System.arraycopy(
        encoded,
        lengthOffset + 1 + previousLength,
        replaced,
        lengthOffset + 1 + replacement.length,
        encoded.length - lengthOffset - 1 - previousLength);
    return replaced;
  }

  private static AttestationBookOperation successor(
      TestCredential founder, AttestationHash previousHead) {
    return successor(
        founder, AttestationAuthorizationTestSupport.BOOK_ID, BigInteger.ONE, previousHead);
  }

  private static AttestationBookOperation rekey(
      TestCredential founder,
      BigInteger order,
      AttestationHash previousHead,
      BigInteger requestEpoch,
      BigInteger effectEpoch) {
    Instant recordedAt = Instant.parse("2026-07-20T00:00:00.001Z");
    return rekey(founder, order, previousHead, requestEpoch, effectEpoch, recordedAt, recordedAt);
  }

  private static AttestationBookOperation rekey(
      TestCredential founder,
      BigInteger order,
      AttestationHash previousHead,
      BigInteger requestEpoch,
      BigInteger effectEpoch,
      Instant recordedAt,
      Instant rekeyedAt) {
    AttestationOperationPreimages requestProjection =
        AttestationLifecycleMutationProjection.rekeyBook(
            AttestationOperationKind.REKEY_BOOK.wireToken(),
            requestEpoch,
            rekeyedAt,
            java.util.Optional.empty());
    AttestationOperationPreimages effectProjection =
        AttestationLifecycleMutationProjection.rekeyBook(
            AttestationOperationKind.REKEY_BOOK.wireToken(),
            effectEpoch,
            rekeyedAt,
            java.util.Optional.empty());
    AttestationPreimage request =
        AttestationPreimage.decode(
            requestProjection.request(), AttestationAuthorizationFailure.PREIMAGE_INVALID);
    AttestationPreimage effect =
        AttestationPreimage.decode(
            effectProjection.effect(), AttestationAuthorizationFailure.PREIMAGE_INVALID);
    AttestationOperationPayload payload =
        new AttestationOperationPayload(
            AttestationAuthorizationTestSupport.BOOK_ID,
            order,
            AttestationOperationKind.REKEY_BOOK.wireToken(),
            previousHead,
            recordedAt,
            AttestationHash.sha256(request.encoded()),
            AttestationHash.sha256(effect.encoded()));
    AttestationAuthorizationEnvelope envelope =
        new AttestationAuthorizationEnvelope(
            payload.encoded(),
            AttestationAuthorizationTestSupport.orderedEntries(payload.encoded(), founder));
    return AttestationBookOperation.decode(
        envelopeBytes(payload, envelope), request.encoded(), effect.encoded());
  }

  private static AttestationBookOperation signedOperation(
      TestCredential founder,
      UUID bookId,
      BigInteger order,
      AttestationOperationKind operationKind,
      AttestationHash previousHead,
      AttestationPreimage request,
      AttestationPreimage effect,
      Instant recordedAt) {
    AttestationOperationPayload payload =
        new AttestationOperationPayload(
            bookId,
            order,
            operationKind.wireToken(),
            previousHead,
            recordedAt,
            AttestationHash.sha256(request.encoded()),
            AttestationHash.sha256(effect.encoded()));
    AttestationAuthorizationEnvelope envelope =
        new AttestationAuthorizationEnvelope(
            payload.encoded(),
            AttestationAuthorizationTestSupport.orderedEntries(payload.encoded(), founder));
    return AttestationBookOperation.decode(
        envelopeBytes(payload, envelope), request.encoded(), effect.encoded());
  }

  private static AttestationPreimage decodePreimage(byte[] encoded) {
    return AttestationPreimage.decode(encoded, AttestationAuthorizationFailure.PREIMAGE_INVALID);
  }

  private static AttestationBookOperation successor(
      TestCredential founder, UUID bookId, BigInteger order, AttestationHash previousHead) {
    UUID backupId = UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100");
    AttestationHash artifactDigest = AttestationHash.sha256(new byte[] {2});
    AttestationPreimage request = backupRequest(backupId, artifactDigest, previousHead);
    AttestationPreimage effect = backupEffect(backupId, artifactDigest, previousHead);
    AttestationOperationPayload payload =
        new AttestationOperationPayload(
            bookId,
            order,
            AttestationOperationKind.BACKUP_CREATED.wireToken(),
            previousHead,
            Instant.parse("2026-07-20T00:00:00.001Z"),
            AttestationHash.sha256(request.encoded()),
            AttestationHash.sha256(effect.encoded()));
    AttestationAuthorizationEnvelope envelope =
        new AttestationAuthorizationEnvelope(
            payload.encoded(),
            AttestationAuthorizationTestSupport.orderedEntries(payload.encoded(), founder));
    return AttestationBookOperation.decode(
        envelopeBytes(payload, envelope), request.encoded(), effect.encoded());
  }

  private static AttestationPreimage backupRequest(
      UUID backupId, AttestationHash artifactDigest, AttestationHash sourceHead) {
    return AttestationPreimage.of(
        List.of(
            command(AttestationOperationKind.BACKUP_CREATED),
            new AttestationPreimage.Fact(
                0x0150,
                List.of(
                    AttestationField.present(AttestationBinaryFieldValue.uuid(backupId)),
                    AttestationField.present(AttestationBinaryFieldValue.hash(artifactDigest)),
                    AttestationField.present(
                        AttestationNumericFieldValue.unsigned64(BigInteger.ZERO)),
                    AttestationField.present(AttestationBinaryFieldValue.hash(sourceHead))))));
  }

  private static AttestationPreimage backupEffect(
      UUID backupId, AttestationHash artifactDigest, AttestationHash sourceHead) {
    return backupEffect(
        backupId, artifactDigest, sourceHead, AttestationEffectMutation.ACKNOWLEDGE);
  }

  private static AttestationPreimage backupEffect(
      UUID backupId,
      AttestationHash artifactDigest,
      AttestationHash sourceHead,
      AttestationEffectMutation mutation) {
    return AttestationPreimage.of(
        List.of(
            new AttestationPreimage.Fact(
                0x0006,
                List.of(
                    AttestationField.present(
                        AttestationNumericFieldValue.mutation(mutation.wireValue())),
                    AttestationField.present(AttestationBinaryFieldValue.uuid(backupId)),
                    AttestationField.present(AttestationBinaryFieldValue.hash(artifactDigest)),
                    AttestationField.present(
                        AttestationNumericFieldValue.unsigned64(BigInteger.ZERO)),
                    AttestationField.present(AttestationBinaryFieldValue.hash(sourceHead))))));
  }

  private static AttestationPreimage.Fact command(AttestationOperationKind operationKind) {
    return new AttestationPreimage.Fact(
        0x0100,
        List.of(
            AttestationField.present(AttestationTextFieldValue.token(operationKind.wireToken())),
            AttestationField.absent(),
            AttestationField.absent(),
            AttestationField.present(
                AttestationTextFieldValue.token(AttestationSourceChannel.CLI.wireToken()))));
  }

  private static byte[] envelopeBytes(
      AttestationOperationPayload payload, AttestationAuthorizationEnvelope authorizationEnvelope) {
    return AttestationEnvelope.of(payload, authorizationEnvelope.entries()).encoded();
  }
}
