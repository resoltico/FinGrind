package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisContext;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisEffectPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisPayload;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisRequestPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.signedGenesisEnvelope;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Constructs attestation artifacts and mutations used by {@link AttestationArtifactVerifierTest}.
 */
final class AttestationArtifactVerifierFixtures {
  private AttestationArtifactVerifierFixtures() {}

  static int manifestBookIdOffset(byte[] snapshot) {
    return snapshot.length + 9;
  }

  static int manifestSourceOrderOffset(byte[] snapshot) {
    return manifestBookIdOffset(snapshot) + 32;
  }

  static int manifestSourceHeadOffset(byte[] snapshot) {
    return manifestSourceOrderOffset(snapshot) + Long.BYTES;
  }

  static int manifestSnapshotDigestOffset(byte[] snapshot) {
    return manifestSourceHeadOffset(snapshot) + AttestationHash.BYTE_LENGTH;
  }

  static int manifestAlgorithmValueOffset(byte[] snapshot) {
    return manifestSnapshotDigestOffset(snapshot) + AttestationHash.BYTE_LENGTH + 1;
  }

  static int receiptAlgorithmValueOffset() {
    return 8 + 1 + 16 + Long.BYTES + AttestationHash.BYTE_LENGTH + 24 + 1;
  }

  static byte[] replaceManifestAlgorithmId(
      byte[] artifact, byte[] snapshot, String replacementAlgorithmId) {
    byte[] replaced =
        replaceAlgorithmId(
            artifact, manifestAlgorithmValueOffset(snapshot) - 1, replacementAlgorithmId);
    int originalManifestLength =
        ByteBuffer.wrap(artifact, artifact.length - Integer.BYTES, Integer.BYTES).getInt();
    int replacementManifestLength = originalManifestLength + replaced.length - artifact.length;
    ByteBuffer.wrap(replaced, replaced.length - Integer.BYTES, Integer.BYTES)
        .putInt(replacementManifestLength);
    return replaced;
  }

  static byte[] replaceAlgorithmId(
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

  static int trailerSnapshotLengthOffset(byte[] artifact) {
    return artifact.length - 21 + 9;
  }

  static AttestationStaticCorpus.Fixture fixture(String id, byte[] source, int offset) {
    byte[] replacement = new byte[] {(byte) (source[offset] ^ 1)};
    return AttestationStaticCorpus.fixture(
        id,
        source,
        AttestationStaticCorpus.Mutation.replace(offset, replacement),
        new AttestationStaticCorpus.PolicyFold(
            BigInteger.ONE, AttestationCapability.BACKUP, 1, 1, 1, 0, false),
        AttestationStaticCorpus.VerificationScope.ARTIFACT,
        AttestationAuthorizationFailure.MANIFEST_INVALID);
  }

  static byte[] artifact(
      TestCredential founder,
      AttestationBookVerification verification,
      byte[] snapshot,
      AttestationHash snapshotDigest) {
    return artifact(
        founder,
        verification.bookId(),
        verification.headOrder(),
        verification.head(),
        snapshot,
        snapshotDigest);
  }

  static byte[] artifact(
      TestCredential founder,
      UUID bookId,
      BigInteger sourceOrder,
      AttestationHash sourceHead,
      byte[] snapshot,
      AttestationHash snapshotDigest) {
    AttestationBackupManifestPayload payload =
        new AttestationBackupManifestPayload(
            bookId,
            UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100"),
            sourceOrder,
            sourceHead,
            snapshotDigest);
    AttestationEnvelope<AttestationBackupManifestPayload> manifest =
        AttestationEnvelope.of(
            payload,
            AttestationAuthorizationTestSupport.orderedEntries(payload.encoded(), founder));
    return new AttestationArtifactContainer(snapshot, manifest).encoded();
  }

  static byte[] receipt(TestCredential founder, AttestationBookVerification verification) {
    AttestationReceiptPayload payload =
        new AttestationReceiptPayload(
            verification.bookId(),
            verification.headOrder(),
            verification.head(),
            Instant.parse("2026-07-20T00:00:01.000Z"));
    return envelope(payload, founder);
  }

  static <P extends AttestationPayload> byte[] envelope(P payload, TestCredential founder) {
    return AttestationEnvelope.of(
            payload, AttestationAuthorizationTestSupport.orderedEntries(payload.encoded(), founder))
        .encoded();
  }

  static AttestationSnapshotDecoder snapshotDecoder(byte[] expectedSnapshot, AttestationBook book) {
    return snapshot -> {
      if (!Arrays.equals(expectedSnapshot, snapshot)) {
        throw new IllegalArgumentException("Unexpected snapshot bytes.");
      }
      return book;
    };
  }

  static AttestationBook book(TestCredential founder) {
    return book(founder, AttestationEffectMutation.ACKNOWLEDGE);
  }

  static AttestationBook book(TestCredential founder, AttestationEffectMutation backupMutation) {
    AttestationPreimage genesisRequest = genesisRequestPreimage(founder);
    AttestationPreimage genesisEffect = genesisEffectPreimage(founder);
    AttestationOperationPayload genesisPayload =
        genesisPayload(
            BigInteger.ZERO,
            AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]),
            genesisRequest,
            genesisEffect);
    AttestationBookOperation genesis =
        AttestationBookOperation.decode(
            envelopeBytes(genesisPayload, signedGenesisEnvelope(genesisContext(founder), founder)),
            genesisRequest.encoded(),
            genesisEffect.encoded());
    UUID backupId = UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100");
    AttestationHash artifactDigest = AttestationHash.sha256(new byte[] {2});
    AttestationPreimage request =
        backupRequest(backupId, artifactDigest, genesis.envelope().head());
    AttestationPreimage effect =
        backupEffect(backupId, artifactDigest, genesis.envelope().head(), backupMutation);
    AttestationOperationPayload successorPayload =
        new AttestationOperationPayload(
            AttestationAuthorizationTestSupport.BOOK_ID,
            BigInteger.ONE,
            AttestationOperationKind.BACKUP_CREATED.wireToken(),
            genesis.envelope().head(),
            Instant.parse("2026-07-20T00:00:00.001Z"),
            AttestationHash.sha256(request.encoded()),
            AttestationHash.sha256(effect.encoded()));
    AttestationBookOperation successor =
        AttestationBookOperation.decode(
            envelope(successorPayload, founder), request.encoded(), effect.encoded());
    return new AttestationBook(List.of(genesis, successor));
  }

  static List<AttestationEvidence> evidence(AttestationBook book) {
    return book.operations().stream()
        .map(
            operation ->
                new AttestationEvidence(
                    operation.envelope().encoded(),
                    operation.requestPreimage().encoded(),
                    operation.effectPreimage().encoded()))
        .toList();
  }

  private static byte[] envelopeBytes(
      AttestationOperationPayload payload, AttestationAuthorizationEnvelope authorizationEnvelope) {
    return AttestationEnvelope.of(payload, authorizationEnvelope.entries()).encoded();
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
}
