package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/** Executes every positive Slice 4 source from its deterministic raw resource. */
class AttestationStaticCorpusFixtureTest {
  @Test
  void executesEveryProtectedBookPositiveFromItsDeclaredRawResource() {
    assertValidBook(AttestationCorpusFixtures.b01());
    assertValidBook(AttestationCorpusFixtures.b02());
    assertValidBook(AttestationCorpusFixtures.b03());
    assertValidBook(AttestationCorpusFixtures.b04());
    assertValidBook(AttestationCorpusFixtures.b05());
    assertValidBook(AttestationCorpusFixtures.b06());
    assertValidBook(AttestationCorpusFixtures.b07());
    assertValidBook(AttestationCorpusFixtures.b10());
  }

  @Test
  void pinsEveryCompleteRawResourceToItsDeclaredFingerprint() {
    assertFingerprint(
        "B-01",
        AttestationCorpusFixtures.b01().encoded(),
        "f31e017a7bee0930759acd9185b5ee656e8ddc04dda893bfbd3be5f8d1b15549");
    assertFingerprint(
        "B-02",
        AttestationCorpusFixtures.b02().encoded(),
        "71c60316634958f786967b7a43452c84be7b4a0e1227a0e629d2f8209271ab1c");
    assertFingerprint(
        "B-03",
        AttestationCorpusFixtures.b03().encoded(),
        "03c337afaf8b89f99999c0095b8d217f8b2335249c24c3f16c26a85c6e9f753c");
    assertFingerprint(
        "B-04",
        AttestationCorpusFixtures.b04().encoded(),
        "5063d4117286b8fc885a9abfb7e9d7f23f5f2e6114df4b53c952099cb81e12b5");
    assertFingerprint(
        "B-05 artifact",
        AttestationCorpusFixtures.b05Artifact().encoded(),
        "b92bba455ca0b086deb84c8e443e837e22ade75176a8153f1a09321a124323fe");
    assertFingerprint(
        "B-05 book",
        AttestationCorpusFixtures.b05().encoded(),
        "0bf16602e98bed75f7531ecbb99eee26452c7a130ad0dd6b57454750ed5d63b2");
    assertFingerprint(
        "B-06",
        AttestationCorpusFixtures.b06().encoded(),
        "fe803e5313a9a3522fc03b683cfe5cbe5435026b4e609af6c7dac9132e28ab2b");
    assertFingerprint(
        "B-07",
        AttestationCorpusFixtures.b07().encoded(),
        "f7f83b2815c876d763500fa52514eb0f1b5e7897b29f451802dfdd8e4f2ab541");
    assertFingerprint(
        "B-10",
        AttestationCorpusFixtures.b10().encoded(),
        "35bd0a4635780f4e8626e055eb23f80ae252ab9704f813e4e0459ba1e9204f01");
    assertFingerprint(
        "B-11 receipt",
        AttestationCorpusFixtures.b11().encoded(),
        "6bdfc070fabc1415b634066bcddeee8cf1fd4a58f9bd39fde8c6a132402b010f");
  }

  @Test
  void distinguishesAcknowledgedAndUnacknowledgedBackupSourcesForRestore() {
    assertRestoreScenario(AttestationCorpusFixtures.b06Restore(), true);
    assertRestoreScenario(AttestationCorpusFixtures.b07Restore(), false);
  }

  @Test
  void executesN11ByMutatingTheCommonPostingPreviousHeadInTheRawBookResource() {
    AttestationCorpusResources.Book base = AttestationCorpusFixtures.b02();
    byte[] baseSource = base.encoded();
    AttestationHash expectedPreviousHead = base.operations().get(2).envelope().head();
    int offset = indexOf(baseSource, expectedPreviousHead.bytes());
    byte[] zeroHead = new byte[AttestationHash.BYTE_LENGTH];
    AttestationStaticCorpus.Fixture fixture =
        AttestationStaticCorpus.fixture(
            "N-11",
            baseSource,
            AttestationStaticCorpus.Mutation.replace(offset, zeroHead),
            new AttestationStaticCorpus.PolicyFold("B-02 POST M=2 before the common posting"),
            AttestationStaticCorpus.VerificationScope.BOOK,
            AttestationAuthorizationFailure.PREVIOUS_HEAD_INVALID);

    assertFailure(
        fixture.expectedFirstFailure(),
        () ->
            AttestationBookVerifier.verify(
                AttestationCorpusFixtures.decodeBook(fixture.source()).decode()));
  }

  @Test
  void executesN14AgainstThePublishedContainerBytes() throws IOException {
    byte[] source =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-CONTAINER-01", "container");
    byte[] snapshot =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-CONTAINER-01", "snapshot");
    int manifestOffset = snapshot.length;
    assertN14(
        "N-14a", source, manifestOffset + 9 + 16 + 16 + Long.BYTES + AttestationHash.BYTE_LENGTH);
    assertN14("N-14b", source, manifestOffset + 9 + 16 + 16 + Long.BYTES);
    assertN14("N-14c", source, manifestOffset + 9);
    assertN14("N-14d", source, source.length - 21 + 9);
  }

  @Test
  void executesTheBackupArtifactAndReceiptFromTheirDeclaredRawResources() {
    AttestationCorpusResources.Artifact artifact = AttestationCorpusFixtures.b05Artifact();
    AttestationStaticCorpus.Fixture artifactFixture =
        AttestationStaticCorpus.positive(
            artifact.id(),
            artifact.encoded(),
            new AttestationStaticCorpus.PolicyFold("BACKUP M=1 at source order 3"),
            AttestationStaticCorpus.VerificationScope.ARTIFACT);
    assertDoesNotThrow(
        () ->
            AttestationArtifactVerifier.verifyArtifact(
                artifactFixture.source(),
                source -> AttestationCorpusFixtures.decodeBook(source).decode()),
        artifactFixture.id());

    AttestationCorpusResources.Receipt receipt = AttestationCorpusFixtures.b11();
    AttestationStaticCorpus.Fixture receiptFixture =
        AttestationStaticCorpus.positive(
            receipt.id(),
            receipt.encoded(),
            new AttestationStaticCorpus.PolicyFold("ANCHOR M=1 at operation order 3"),
            AttestationStaticCorpus.VerificationScope.RECEIPT);
    AttestationBookVerification verification =
        AttestationBookVerifier.verify(
            AttestationCorpusFixtures.decodeBook(receipt.book().encoded()).decode());
    assertDoesNotThrow(
        () ->
            AttestationArtifactVerifier.verifyReceipt(
                receiptFixture.source(), verification, AttestationReceiptRetention.INDEPENDENT),
        receiptFixture.id());
  }

  @Test
  void executesTheStandaloneEnvelopeResourcesFromTheirDeclaredRawResources() {
    AttestationCorpusResources.StandaloneEnvelope manifest = AttestationCorpusFixtures.b08();
    AttestationStaticCorpus.Fixture manifestFixture =
        AttestationStaticCorpus.positive(
            manifest.id(),
            manifest.encoded(),
            new AttestationStaticCorpus.PolicyFold("BACKUP M=2 with A and B granted"),
            AttestationStaticCorpus.VerificationScope.AUTHORIZATION);
    AttestationDecodedEnvelope<AttestationBackupManifestPayload> decodedManifest =
        AttestationDecodedEnvelope.manifest(manifestFixture.source());
    assertDoesNotThrow(
        () ->
            AttestationAuthorization.requireAuthorized(
                manifest.registry(),
                AttestationAuthorizationContext.manifest(decodedManifest.payload()),
                decodedManifest.authorizationEnvelope()),
        manifestFixture.id());

    AttestationCorpusResources.StandaloneEnvelope receipt = AttestationCorpusFixtures.b09();
    AttestationStaticCorpus.Fixture receiptFixture =
        AttestationStaticCorpus.positive(
            receipt.id(),
            receipt.encoded(),
            new AttestationStaticCorpus.PolicyFold("ANCHOR M=2 with A and B granted"),
            AttestationStaticCorpus.VerificationScope.AUTHORIZATION);
    AttestationDecodedEnvelope<AttestationReceiptPayload> decodedReceipt =
        AttestationDecodedEnvelope.receipt(receiptFixture.source());
    assertDoesNotThrow(
        () ->
            AttestationAuthorization.requireAuthorized(
                receipt.registry(),
                AttestationAuthorizationContext.receipt(decodedReceipt.payload()),
                decodedReceipt.authorizationEnvelope()),
        receiptFixture.id());
  }

  private static int indexOf(byte[] source, byte[] target) {
    for (int offset = 0; offset <= source.length - target.length; offset++) {
      boolean matches = true;
      for (int index = 0; index < target.length; index++) {
        if (source[offset + index] != target[index]) {
          matches = false;
          break;
        }
      }
      if (matches) {
        return offset;
      }
    }
    throw new IllegalArgumentException(
        "Corpus mutation bytes are not present in the base resource.");
  }

  private static void assertValidBook(AttestationCorpusResources.Book resource) {
    AttestationStaticCorpus.Fixture fixture =
        AttestationStaticCorpus.positive(
            resource.id(),
            resource.encoded(),
            new AttestationStaticCorpus.PolicyFold("declared by the resource genesis and history"),
            AttestationStaticCorpus.VerificationScope.BOOK);
    AttestationBookVerification verification =
        assertDoesNotThrow(
            () ->
                AttestationBookVerifier.verify(
                    AttestationCorpusFixtures.decodeBook(fixture.source()).decode()),
            fixture.id());
    assertEquals(BigInteger.valueOf(resource.operations().size() - 1L), verification.headOrder());
  }

  private static void assertRestoreScenario(
      AttestationCorpusResources.Restore restore, boolean acknowledgedAtSource) {
    assertEquals(acknowledgedAtSource, restore.sourceAcknowledgement().isPresent(), restore.id());
    assertDoesNotThrow(
        () ->
            AttestationArtifactVerifier.verifyArtifact(
                restore.artifact().encoded(),
                source -> AttestationCorpusFixtures.decodeBook(source).decode()),
        restore.id());
    restore
        .sourceAcknowledgement()
        .ifPresent(
            acknowledgement -> {
              AttestationBookVerification verification =
                  AttestationBookVerifier.verify(acknowledgement.decode());
              assertEquals(BigInteger.valueOf(4), verification.headOrder(), restore.id());
              assertEquals(
                  AttestationOperationKind.BACKUP_CREATED.wireToken(),
                  acknowledgement.operations().get(4).envelope().payload().operationKind(),
                  restore.id());
            });
    AttestationBookVerification targetVerification =
        assertDoesNotThrow(
            () -> AttestationBookVerifier.verify(restore.target().decode()), restore.id());
    assertEquals(BigInteger.valueOf(4), targetVerification.headOrder(), restore.id());
    assertEquals(AttestationCorpusFixtures.BOOK_ID, targetVerification.bookId(), restore.id());
  }

  private static void assertFingerprint(String id, byte[] source, String expectedHash) {
    assertFalse(source.length == 0, id);
    assertEquals(expectedHash, AttestationHash.sha256(source).hex(), id);
  }

  private static void assertN14(String id, byte[] source, int offset) {
    AttestationStaticCorpus.Fixture fixture =
        AttestationStaticCorpus.fixture(
            id,
            source,
            AttestationStaticCorpus.Mutation.replace(
                offset, new byte[] {(byte) (source[offset] ^ 1)}),
            new AttestationStaticCorpus.PolicyFold("BACKUP M=1 at the published artifact source"),
            AttestationStaticCorpus.VerificationScope.ARTIFACT,
            AttestationAuthorizationFailure.MANIFEST_INVALID);
    assertFailure(
        fixture.expectedFirstFailure(),
        () ->
            AttestationArtifactVerifier.verifyArtifact(
                fixture.source(),
                ignored -> {
                  throw new IllegalArgumentException();
                }));
  }
}
