package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisContext;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisEffectPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisPayload;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisRequestPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.signedGenesisEnvelope;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves that external artifacts bind to the book reconstructed from their own snapshot bytes. */
class AttestationArtifactVerifierTest {
  @Test
  void verifiesAManifestAgainstItsSnapshotChainAndReportsANonIndependentReceipt() {
    TestCredential founder = credential();
    AttestationBook book = book(founder);
    AttestationBookVerification verification = AttestationBookVerifier.verify(book);
    byte[] snapshot = new byte[] {1, 2, 3, 4};
    byte[] artifact = artifact(founder, verification, snapshot, AttestationHash.sha256(snapshot));

    AttestationArtifactVerification artifactVerification =
        AttestationArtifactVerifier.verifyArtifact(artifact, snapshotDecoder(snapshot, book));
    AttestationReceiptVerification receiptVerification =
        AttestationArtifactVerifier.verifyReceipt(
            receipt(founder, verification),
            artifactVerification.snapshotVerification(),
            AttestationReceiptRetention.WITHIN_BOOK_TRUST_BOUNDARY);
    AttestationReceiptVerification independentReceiptVerification =
        AttestationArtifactVerifier.verifyReceipt(
            receipt(founder, verification),
            artifactVerification.snapshotVerification(),
            AttestationReceiptRetention.INDEPENDENT);

    assertEquals(verification.head(), artifactVerification.snapshotVerification().head());
    assertEquals(
        List.of(AttestationReceiptFinding.NOT_INDEPENDENT), receiptVerification.findings());
    assertEquals("receipt-not-independent", receiptVerification.findings().getFirst().code());
    assertEquals(List.of(), independentReceiptVerification.findings());
  }

  @Test
  void rejectsManifestSnapshotMismatchBeforeManifestAuthorization() {
    TestCredential founder = credential();
    AttestationBook book = book(founder);
    AttestationBookVerification verification = AttestationBookVerifier.verify(book);
    byte[] snapshot = new byte[] {1, 2, 3, 4};
    byte[] artifact =
        artifact(founder, verification, snapshot, AttestationHash.sha256(new byte[] {9, 8, 7, 6}));

    assertFailure(
        AttestationAuthorizationFailure.MANIFEST_INVALID,
        () ->
            AttestationArtifactVerifier.verifyArtifact(artifact, snapshotDecoder(snapshot, book)));
  }

  @Test
  void rejectsManifestSnapshotMismatchBeforeDependentSnapshotFailure() {
    TestCredential founder = credential();
    AttestationBookVerification verification = AttestationBookVerifier.verify(book(founder));
    byte[] snapshot = new byte[] {1, 2, 3, 4};
    byte[] artifact =
        artifact(founder, verification, snapshot, AttestationHash.sha256(new byte[] {9, 8, 7, 6}));

    assertFailure(
        AttestationAuthorizationFailure.MANIFEST_INVALID,
        () ->
            AttestationArtifactVerifier.verifyArtifact(
                artifact,
                ignored -> {
                  throw new AttestationAuthorizationException(
                      AttestationAuthorizationFailure.PREVIOUS_HEAD_INVALID);
                }));
  }

  @Test
  void rejectsManifestWhoseSnapshotContainsOperationsPastItsDeclaredSourceHead() {
    TestCredential founder = credential();
    AttestationBook snapshotBook = book(founder);
    AttestationBookVerification verification = AttestationBookVerifier.verify(snapshotBook);
    AttestationBookVerification genesisVerification =
        AttestationBookVerifier.verify(
            new AttestationBook(List.of(snapshotBook.operations().getFirst())));
    byte[] snapshot = new byte[] {1, 2, 3, 4};
    byte[] artifact =
        artifact(
            founder,
            verification.bookId(),
            genesisVerification.headOrder(),
            genesisVerification.head(),
            snapshot,
            AttestationHash.sha256(snapshot));

    assertFailure(
        AttestationAuthorizationFailure.MANIFEST_INVALID,
        () ->
            AttestationArtifactVerifier.verifyArtifact(
                artifact, snapshotDecoder(snapshot, snapshotBook)));
  }

  @Test
  void rejectsReceiptThatNamesTheRightBookButTheWrongHistoricalHead() {
    TestCredential founder = credential();
    AttestationBookVerification verification = AttestationBookVerifier.verify(book(founder));
    AttestationReceiptPayload payload =
        new AttestationReceiptPayload(
            verification.bookId(),
            verification.headOrder(),
            AttestationHash.sha256(new byte[] {9}),
            Instant.parse("2026-07-20T00:00:01.000Z"));

    assertFailure(
        AttestationAuthorizationFailure.RECEIPT_INVALID,
        () ->
            AttestationArtifactVerifier.verifyReceipt(
                envelope(payload, founder), verification, AttestationReceiptRetention.INDEPENDENT));
  }

  @Test
  void rejectsReceiptWithTheWrongBookAndAReferenceBeyondTheVerifiedHead() {
    TestCredential founder = credential();
    AttestationBookVerification verification = AttestationBookVerifier.verify(book(founder));

    AttestationReceiptPayload wrongBook =
        new AttestationReceiptPayload(
            UUID.fromString("11000000-0000-0000-0000-000000000001"),
            verification.headOrder(),
            verification.head(),
            Instant.parse("2026-07-20T00:00:01.000Z"));
    AttestationReceiptPayload futureOrder =
        new AttestationReceiptPayload(
            verification.bookId(),
            verification.headOrder().add(BigInteger.ONE),
            verification.head(),
            Instant.parse("2026-07-20T00:00:01.000Z"));

    assertFailure(
        AttestationAuthorizationFailure.RECEIPT_INVALID,
        () ->
            AttestationArtifactVerifier.verifyReceipt(
                envelope(wrongBook, founder),
                verification,
                AttestationReceiptRetention.INDEPENDENT));
    assertFailure(
        AttestationAuthorizationFailure.RECEIPT_INVALID,
        () ->
            AttestationArtifactVerifier.verifyReceipt(
                envelope(futureOrder, founder),
                verification,
                AttestationReceiptRetention.INDEPENDENT));
  }

  @Test
  void rejectsEveryManifestBindingComponentAndARejectedSnapshotDecoder() {
    TestCredential founder = credential();
    AttestationBook book = book(founder);
    AttestationBookVerification verification = AttestationBookVerifier.verify(book);
    byte[] snapshot = new byte[] {1, 2, 3, 4};
    byte[] artifact = artifact(founder, verification, snapshot, AttestationHash.sha256(snapshot));

    assertFailure(
        AttestationAuthorizationFailure.MANIFEST_INVALID,
        () ->
            AttestationArtifactVerifier.verifyArtifact(
                artifact,
                ignored -> {
                  throw new IllegalStateException();
                }));
    assertManifestFailure(artifact, snapshot, book, manifestBookIdOffset(snapshot));
    assertManifestFailure(artifact, snapshot, book, manifestSourceOrderOffset(snapshot));
    assertManifestFailure(artifact, snapshot, book, manifestSourceHeadOffset(snapshot));
  }

  @Test
  void rejectsEachConstructedArtifactManifestBindingMutation() {
    TestCredential founder = credential();
    AttestationBook book = book(founder);
    AttestationBookVerification verification = AttestationBookVerifier.verify(book);
    byte[] snapshot = new byte[] {1, 2, 3, 4};
    byte[] artifact = artifact(founder, verification, snapshot, AttestationHash.sha256(snapshot));

    assertManifestFixture(
        fixture("manifest-snapshot-digest", artifact, manifestSnapshotDigestOffset(snapshot)),
        snapshot,
        book);
    assertManifestFixture(
        fixture("manifest-source-head", artifact, manifestSourceHeadOffset(snapshot)),
        snapshot,
        book);
    assertManifestFixture(
        fixture("manifest-book-id", artifact, manifestBookIdOffset(snapshot)), snapshot, book);
    assertManifestFixture(
        fixture("manifest-trailer-length", artifact, trailerSnapshotLengthOffset(artifact)),
        snapshot,
        book);
  }

  private static void assertManifestFailure(
      byte[] artifact, byte[] snapshot, AttestationBook book, int offset) {
    byte[] tampered = artifact.clone();
    tampered[offset] ^= 1;
    assertFailure(
        AttestationAuthorizationFailure.MANIFEST_INVALID,
        () ->
            AttestationArtifactVerifier.verifyArtifact(tampered, snapshotDecoder(snapshot, book)));
  }

  private static int manifestBookIdOffset(byte[] snapshot) {
    return snapshot.length + 9;
  }

  private static int manifestSourceOrderOffset(byte[] snapshot) {
    return manifestBookIdOffset(snapshot) + 32;
  }

  private static int manifestSourceHeadOffset(byte[] snapshot) {
    return manifestSourceOrderOffset(snapshot) + Long.BYTES;
  }

  private static int manifestSnapshotDigestOffset(byte[] snapshot) {
    return manifestSourceHeadOffset(snapshot) + AttestationHash.BYTE_LENGTH;
  }

  private static int trailerSnapshotLengthOffset(byte[] artifact) {
    return artifact.length - 21 + 9;
  }

  private static AttestationStaticCorpus.Fixture fixture(String id, byte[] source, int offset) {
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

  private static void assertManifestFixture(
      AttestationStaticCorpus.Fixture fixture, byte[] snapshot, AttestationBook book) {
    assertTrue(fixture.source().length > 0);
    assertFailure(
        fixture.expectedFirstFailure(),
        () ->
            AttestationArtifactVerifier.verifyArtifact(
                fixture.source(), snapshotDecoder(snapshot, book)));
  }

  private static byte[] artifact(
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

  private static byte[] artifact(
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

  private static byte[] receipt(TestCredential founder, AttestationBookVerification verification) {
    AttestationReceiptPayload payload =
        new AttestationReceiptPayload(
            verification.bookId(),
            verification.headOrder(),
            verification.head(),
            Instant.parse("2026-07-20T00:00:01.000Z"));
    return envelope(payload, founder);
  }

  private static <P extends AttestationPayload> byte[] envelope(P payload, TestCredential founder) {
    return AttestationEnvelope.of(
            payload, AttestationAuthorizationTestSupport.orderedEntries(payload.encoded(), founder))
        .encoded();
  }

  private static AttestationSnapshotDecoder snapshotDecoder(
      byte[] expectedSnapshot, AttestationBook book) {
    return snapshot -> {
      if (!Arrays.equals(expectedSnapshot, snapshot)) {
        throw new IllegalArgumentException("Unexpected snapshot bytes.");
      }
      return book;
    };
  }

  private static AttestationBook book(TestCredential founder) {
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
    AttestationPreimage effect = backupEffect(backupId, artifactDigest, genesis.envelope().head());
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
}
