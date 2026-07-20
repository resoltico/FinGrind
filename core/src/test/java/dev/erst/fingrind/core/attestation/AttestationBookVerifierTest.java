package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisContext;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisEffectPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisPayload;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisRequestPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.signedGenesisEnvelope;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    UUID backupId = UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100");
    AttestationHash artifactDigest = AttestationHash.sha256(new byte[] {2});
    AttestationPreimage request = backupRequest(backupId, artifactDigest, previousHead);
    AttestationPreimage effect = backupEffect(backupId, artifactDigest, previousHead);
    AttestationOperationPayload payload =
        new AttestationOperationPayload(
            AttestationAuthorizationTestSupport.BOOK_ID,
            BigInteger.ONE,
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
}
