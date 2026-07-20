package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisContext;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisEffectPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisPayload;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisRequestPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.signedGenesisEnvelope;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
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
            List.of(new AttestationCompromiseReview(founder.keyId(), BigInteger.ONE, null)));

    assertEquals(BigInteger.ONE, verification.headOrder());
    assertEquals(successor.envelope().head(), verification.head());
    assertEquals(
        List.of(
            new AttestationReviewFinding(
                new AttestationCompromiseReview(founder.keyId(), BigInteger.ONE, null),
                BigInteger.ONE)),
        verification.reviewFindings());
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
  void executesTheByteAddressedPreviousHeadRowN11() {
    TestCredential founder = credential();
    AttestationBookOperation genesis = genesis(founder);
    AttestationBookOperation malformed =
        successor(founder, AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]));
    byte[] rawSource = malformed.envelope().encoded();
    byte[] replacement = new byte[AttestationHash.BYTE_LENGTH];
    AttestationStaticCorpus.Fixture fixture =
        AttestationStaticCorpus.fixture(
            "N-11",
            rawSource,
            new AttestationStaticCorpus.Mutation(indexOf(rawSource, replacement), replacement),
            new AttestationStaticCorpus.PolicyFold("B-02 POST M=2 before the common posting"),
            AttestationStaticCorpus.VerificationScope.BOOK,
            AttestationAuthorizationFailure.PREVIOUS_HEAD_INVALID);

    assertTrue(fixture.mutation().isRepresentedBy(fixture.rawSource()));
    assertFailure(
        fixture.expectedFirstFailure(),
        () -> AttestationBookVerifier.verify(new AttestationBook(List.of(genesis, malformed))));
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
  void keepsReviewIntervalsAndBookResultAccessorsInternallyConsistent() {
    TestCredential founder = credential();
    AttestationBookOperation genesis = genesis(founder);
    AttestationBookVerification verification =
        AttestationBookVerifier.verify(new AttestationBook(List.of(genesis)));
    AttestationCompromiseReview review =
        new AttestationCompromiseReview(founder.keyId(), BigInteger.ONE, BigInteger.TWO);

    assertFalse(review.includes(BigInteger.ZERO));
    assertTrue(review.includes(BigInteger.ONE));
    assertTrue(review.includes(BigInteger.TWO));
    assertFalse(review.includes(BigInteger.valueOf(3)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationCompromiseReview(founder.keyId(), BigInteger.ONE, BigInteger.ZERO));
    assertThrows(IllegalArgumentException.class, () -> new AttestationBook(List.of()));
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
  void reportsOnlyReviewsThatCoverAnOperationSignedByTheNamedCredential() {
    TestCredential founder = credential();
    AttestationBook book = new AttestationBook(List.of(genesis(founder)));

    assertEquals(
        List.of(),
        AttestationBookVerifier.verify(
                book,
                List.of(
                    new AttestationCompromiseReview(
                        AttestationHash.sha256(new byte[] {9}), BigInteger.ZERO, BigInteger.ZERO),
                    new AttestationCompromiseReview(founder.keyId(), BigInteger.ONE, null)))
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

  private static AttestationBookOperation successor(
      TestCredential founder, AttestationHash previousHead) {
    return successor(
        founder, AttestationAuthorizationTestSupport.BOOK_ID, BigInteger.ONE, previousHead);
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
    return AttestationPreimage.of(
        List.of(
            new AttestationPreimage.Fact(
                0x0006,
                List.of(
                    AttestationField.present(AttestationNumericFieldValue.mutation(0)),
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

  private static int indexOf(byte[] source, byte[] target) {
    for (int offset = 0; offset <= source.length - target.length; offset++) {
      if (java.util.Arrays.equals(
          target, java.util.Arrays.copyOfRange(source, offset, offset + target.length))) {
        return offset;
      }
    }
    throw new IllegalArgumentException("Fixture mutation bytes are not present in the raw source.");
  }
}
