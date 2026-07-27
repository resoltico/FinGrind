package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationArtifactVerifierFixtures.artifact;
import static dev.erst.fingrind.core.attestation.AttestationArtifactVerifierFixtures.book;
import static dev.erst.fingrind.core.attestation.AttestationArtifactVerifierFixtures.envelope;
import static dev.erst.fingrind.core.attestation.AttestationArtifactVerifierFixtures.evidence;
import static dev.erst.fingrind.core.attestation.AttestationArtifactVerifierFixtures.fixture;
import static dev.erst.fingrind.core.attestation.AttestationArtifactVerifierFixtures.manifestAlgorithmValueOffset;
import static dev.erst.fingrind.core.attestation.AttestationArtifactVerifierFixtures.manifestBookIdOffset;
import static dev.erst.fingrind.core.attestation.AttestationArtifactVerifierFixtures.manifestSnapshotDigestOffset;
import static dev.erst.fingrind.core.attestation.AttestationArtifactVerifierFixtures.manifestSourceHeadOffset;
import static dev.erst.fingrind.core.attestation.AttestationArtifactVerifierFixtures.manifestSourceOrderOffset;
import static dev.erst.fingrind.core.attestation.AttestationArtifactVerifierFixtures.receipt;
import static dev.erst.fingrind.core.attestation.AttestationArtifactVerifierFixtures.receiptAlgorithmValueOffset;
import static dev.erst.fingrind.core.attestation.AttestationArtifactVerifierFixtures.replaceAlgorithmId;
import static dev.erst.fingrind.core.attestation.AttestationArtifactVerifierFixtures.replaceManifestAlgorithmId;
import static dev.erst.fingrind.core.attestation.AttestationArtifactVerifierFixtures.snapshotDecoder;
import static dev.erst.fingrind.core.attestation.AttestationArtifactVerifierFixtures.trailerSnapshotLengthOffset;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
  void distinguishesUndecodableReceiptBytesFromAnUnsupportedDecodedReceiptVersion() {
    TestCredential founder = credential();
    AttestationBookVerification verification = AttestationBookVerifier.verify(book(founder));

    AttestationReceiptArtifactException malformed =
        assertThrows(
            AttestationReceiptArtifactException.class,
            () ->
                AttestationArtifactVerifier.verifyReceipt(
                    new byte[] {0}, verification, AttestationReceiptRetention.INDEPENDENT));
    assertInstanceOf(AttestationAuthorizationException.class, malformed.getCause());

    byte[] unsupportedVersion = receipt(founder, verification);
    unsupportedVersion["FGATTRC1".length()] = 2;
    assertFailure(
        AttestationAuthorizationFailure.UNSUPPORTED_VERSION,
        () ->
            AttestationArtifactVerifier.verifyReceipt(
                unsupportedVersion, verification, AttestationReceiptRetention.INDEPENDENT));
  }

  @Test
  void rejectsReceiptBytesPastTheReceiptSpecificWireLimitBeforeEnvelopeDecoding() {
    TestCredential founder = credential();
    AttestationBook book = book(founder);
    AttestationBookVerification verification = AttestationBookVerifier.verify(book);
    byte[] oversizedReceipt = new byte[AttestationReceipt.maximumEncodedByteCount() + 1];

    assertEquals(7_267, AttestationReceipt.maximumEncodedByteCount());
    AttestationReceiptArtifactException failure =
        assertThrows(
            AttestationReceiptArtifactException.class,
            () ->
                AttestationReceipt.verify(
                    oversizedReceipt, evidence(book), AttestationReceiptRetention.INDEPENDENT));

    assertInstanceOf(AttestationAuthorizationException.class, failure.getCause());
    assertThrows(
        AttestationReceiptArtifactException.class,
        () ->
            AttestationArtifactVerifier.verifyReceipt(
                oversizedReceipt, verification, AttestationReceiptRetention.INDEPENDENT));
  }

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
  void verifiesBackupArtifactsFromRawEvidenceAndNormalizesSnapshotReaderFailures() {
    TestCredential founder = credential();
    AttestationBook book = book(founder);
    AttestationBookVerification verification = AttestationBookVerifier.verify(book);
    byte[] snapshot = new byte[] {9, 8, 7, 6};
    byte[] artifact = artifact(founder, verification, snapshot, AttestationHash.sha256(snapshot));

    AttestationBackupArtifactVerification backup =
        AttestationArtifactVerifier.verifyBackupArtifact(
            artifact,
            supplied -> {
              assertTrue(Arrays.equals(snapshot, supplied));
              return evidence(book);
            });

    assertEquals(verification.bookId(), backup.bookId());
    assertEquals(verification.headOrder(), backup.sourceOrder());
    assertEquals(verification.headOrder(), backup.sourceVerification().headOrder());
    assertFailure(
        AttestationAuthorizationFailure.MANIFEST_INVALID,
        () -> AttestationArtifactVerifier.verifyBackupArtifact(artifact, ignored -> List.of()));
    assertFailure(
        AttestationAuthorizationFailure.MANIFEST_INVALID,
        () ->
            AttestationArtifactVerifier.verifyBackupArtifact(
                artifact,
                ignored -> {
                  throw new IllegalStateException("reader failure");
                }));
    assertFailure(
        AttestationAuthorizationFailure.UNSUPPORTED_VERSION,
        () ->
            AttestationArtifactVerifier.verifyBackupArtifact(
                artifact,
                ignored -> {
                  throw new AttestationAuthorizationException(
                      AttestationAuthorizationFailure.UNSUPPORTED_VERSION);
                }));
  }

  @Test
  void sharesLifecycleSemanticChainValidationAcrossBackupAndReceiptArtifacts() {
    TestCredential founder = credential();
    AttestationBook validBook = book(founder);
    AttestationBookVerification verification = AttestationBookVerifier.verify(validBook);
    byte[] snapshot = new byte[] {9, 8, 7, 6};
    byte[] artifact = artifact(founder, verification, snapshot, AttestationHash.sha256(snapshot));
    AttestationBook wrongVerbBook = book(founder, AttestationEffectMutation.CREATE);
    AttestationVerificationException directFailure =
        assertThrows(
            AttestationVerificationException.class,
            () -> AttestationVerifier.verifyBook(evidence(wrongVerbBook)));

    assertEquals("attestation-request-profile-invalid", directFailure.code());
    assertFailure(
        AttestationAuthorizationFailure.MANIFEST_INVALID,
        () ->
            AttestationArtifactVerifier.verifyBackupArtifact(
                artifact, ignored -> evidence(wrongVerbBook)));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationReceipt.verify(
                receipt(founder, verification),
                evidence(wrongVerbBook),
                AttestationReceiptRetention.INDEPENDENT));
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
  void normalizesAnInvalidSnapshotChainToTheManifestTaxonomy() {
    TestCredential founder = credential();
    AttestationBookVerification verification = AttestationBookVerifier.verify(book(founder));
    byte[] snapshot = new byte[] {1, 2, 3, 4};
    byte[] artifact = artifact(founder, verification, snapshot, AttestationHash.sha256(snapshot));

    assertFailure(
        AttestationAuthorizationFailure.MANIFEST_INVALID,
        () ->
            AttestationArtifactVerifier.verifyArtifact(
                artifact,
                ignored -> {
                  throw new AttestationAuthorizationException(
                      AttestationAuthorizationFailure.PREVIOUS_HEAD_INVALID);
                }));
    assertFailure(
        AttestationAuthorizationFailure.UNSUPPORTED_VERSION,
        () ->
            AttestationArtifactVerifier.verifyArtifact(
                artifact,
                ignored -> {
                  throw new AttestationAuthorizationException(
                      AttestationAuthorizationFailure.UNSUPPORTED_VERSION);
                }));
  }

  @Test
  void reportsManifestPreambleFailureBeforeAnUnsupportedPayloadAlgorithm() {
    TestCredential founder = credential();
    AttestationBook book = book(founder);
    AttestationBookVerification verification = AttestationBookVerifier.verify(book);
    byte[] snapshot = new byte[] {1, 2, 3, 4};
    byte[] artifact = artifact(founder, verification, snapshot, AttestationHash.sha256(snapshot));
    byte[] tampered = artifact.clone();
    tampered[manifestSnapshotDigestOffset(snapshot)] ^= 1;
    tampered[manifestAlgorithmValueOffset(snapshot) + "ed25519".length() - 1] = '8';

    assertFailure(
        AttestationAuthorizationFailure.MANIFEST_INVALID,
        () ->
            AttestationArtifactVerifier.verifyArtifact(tampered, snapshotDecoder(snapshot, book)));
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
  void reportsReceiptPreambleFailureBeforeAnUnsupportedPayloadAlgorithm() {
    TestCredential founder = credential();
    AttestationBookVerification verification = AttestationBookVerifier.verify(book(founder));
    AttestationReceiptPayload payload =
        new AttestationReceiptPayload(
            verification.bookId(),
            verification.headOrder(),
            AttestationHash.sha256(new byte[] {9}),
            Instant.parse("2026-07-20T00:00:01.000Z"));
    byte[] receipt = envelope(payload, founder);
    receipt[receiptAlgorithmValueOffset() + "ed25519".length() - 1] = '8';

    assertFailure(
        AttestationAuthorizationFailure.RECEIPT_INVALID,
        () ->
            AttestationArtifactVerifier.verifyReceipt(
                receipt, verification, AttestationReceiptRetention.INDEPENDENT));
  }

  @Test
  void rejectsAnUnsupportedReceiptPayloadAlgorithmAfterItsPreambleIsValid() {
    TestCredential founder = credential();
    AttestationBookVerification verification = AttestationBookVerifier.verify(book(founder));
    byte[] receipt = receipt(founder, verification);
    receipt[receiptAlgorithmValueOffset() + "ed25519".length() - 1] = '8';

    assertFailure(
        AttestationAuthorizationFailure.KEY_ALGORITHM_INVALID,
        () ->
            AttestationArtifactVerifier.verifyReceipt(
                receipt, verification, AttestationReceiptRetention.INDEPENDENT));
  }

  @Test
  void rejectsEveryBoundedAlternateLengthArtifactAlgorithmAtTheSharedAlgorithmCheck() {
    TestCredential founder = credential();
    AttestationBook book = book(founder);
    AttestationBookVerification verification = AttestationBookVerifier.verify(book);
    byte[] snapshot = new byte[] {1, 2, 3, 4};
    byte[] artifact = artifact(founder, verification, snapshot, AttestationHash.sha256(snapshot));
    byte[] receipt = receipt(founder, verification);

    for (String algorithmId : List.of("ed2551", "ed255190")) {
      assertFailure(
          AttestationAuthorizationFailure.KEY_ALGORITHM_INVALID,
          () ->
              AttestationArtifactVerifier.verifyArtifact(
                  replaceManifestAlgorithmId(artifact, snapshot, algorithmId),
                  snapshotDecoder(snapshot, book)));
      assertFailure(
          AttestationAuthorizationFailure.KEY_ALGORITHM_INVALID,
          () ->
              AttestationArtifactVerifier.verifyReceipt(
                  replaceAlgorithmId(receipt, receiptAlgorithmValueOffset() - 1, algorithmId),
                  verification,
                  AttestationReceiptRetention.INDEPENDENT));
    }
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

  private static void assertManifestFixture(
      AttestationStaticCorpus.Fixture fixture, byte[] snapshot, AttestationBook book) {
    assertTrue(fixture.source().length > 0);
    assertFailure(
        fixture.expectedFirstFailure(),
        () ->
            AttestationArtifactVerifier.verifyArtifact(
                fixture.source(), snapshotDecoder(snapshot, book)));
  }
}
