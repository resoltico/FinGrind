package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
