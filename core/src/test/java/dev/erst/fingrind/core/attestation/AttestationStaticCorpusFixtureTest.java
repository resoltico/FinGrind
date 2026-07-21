package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/** Executes every positive Slice 4 source from immutable committed bytes. */
class AttestationStaticCorpusFixtureTest {
  private static final AttestationStaticCorpus.PolicyFold BACKUP_AT_SOURCE_ORDER_THREE =
      new AttestationStaticCorpus.PolicyFold(
          BigInteger.valueOf(3), AttestationCapability.BACKUP, 1, 2, 2, 0, false);
  private static final AttestationStaticCorpus.PolicyFold ANCHOR_AT_ORDER_THREE =
      new AttestationStaticCorpus.PolicyFold(
          BigInteger.valueOf(3), AttestationCapability.ANCHOR, 1, 2, 2, 0, false);
  private static final String V_CONTAINER_DIGEST =
      "3b0fc99b3916dadebfdfa6babcff83afdac8d23b861a4a4e5c43d9e386d9d6ff";
  private static final int N14_BOOK_ID_OFFSET = 25;
  private static final int N14_SOURCE_OPERATION_HEAD_OFFSET = 65;
  private static final int N14_SNAPSHOT_DIGEST_OFFSET = 97;
  private static final int N14_TRAILER_SNAPSHOT_LENGTH_OFFSET = 372;

  @Test
  void executesEveryProtectedBookPositiveFromItsDeclaredRawResource() {
    for (String id : AttestationStaticCorpusVectors.positiveBookIds()) {
      AttestationStaticCorpus.Fixture fixture =
          AttestationStaticCorpusVectors.positiveBookFixture(id);
      AttestationStaticCorpusVectors.requirePolicyFold(
          AttestationStaticCorpusVectors.positivePolicy(id));
      AttestationCorpusResources.Book book =
          AttestationCorpusResources.source(id, fixture.source());
      AttestationBookVerification verification =
          assertDoesNotThrow(() -> AttestationBookVerifier.verify(book.decode()), id);
      assertEquals(BigInteger.valueOf(book.operations().size() - 1L), verification.headOrder(), id);
    }
  }

  @Test
  void verifiesEveryCommittedSourceAgainstItsIndependentFingerprint() {
    for (String id : AttestationStaticCorpusVectors.sourceIds()) {
      assertFalse(AttestationStaticCorpusVectors.source(id).length == 0, id);
    }
  }

  @Test
  void distinguishesAcknowledgedAndUnacknowledgedBackupSourcesForRestore() {
    AttestationCorpusResources.Artifact artifact = AttestationStaticCorpusVectors.artifactB05();
    assertDoesNotThrow(
        () ->
            AttestationArtifactVerifier.verifyArtifact(
                artifact.encoded(),
                source -> AttestationStaticCorpusVectors.book("B-02").decode()));
    AttestationStaticCorpusVectors.requirePolicyFold("B-02", BACKUP_AT_SOURCE_ORDER_THREE);

    AttestationCorpusResources.Book acknowledged = AttestationStaticCorpusVectors.book("B-05-book");
    AttestationBookVerification acknowledgedVerification =
        assertDoesNotThrow(() -> AttestationBookVerifier.verify(acknowledged.decode()));
    assertEquals(BigInteger.valueOf(4), acknowledgedVerification.headOrder());
    assertEquals(
        AttestationOperationKind.BACKUP_CREATED.wireToken(),
        acknowledged.operations().get(4).envelope().payload().operationKind());

    assertRestoreTarget("B-06");
    assertRestoreTarget("B-07");
  }

  @Test
  void executesN11FromItsCommittedMutation() {
    AttestationStaticCorpus.Fixture fixture =
        AttestationStaticCorpusVectors.negativeBookFixture("N-11");
    AttestationStaticCorpusVectors.requirePolicyFold(
        AttestationStaticCorpusVectors.negativePolicy("N-11"));
    assertFailure(
        fixture.expectedFirstFailure(),
        () ->
            AttestationBookVerifier.verify(
                AttestationCorpusResources.source("N-11", fixture.source()).decode()));
  }

  @Test
  void executesN14AgainstThePublishedContainerBytes() throws IOException {
    byte[] source =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-CONTAINER-01", "container");
    assertEquals(V_CONTAINER_DIGEST, AttestationHash.sha256(source).hex());
    assertN14("N-14a", source, N14_SNAPSHOT_DIGEST_OFFSET);
    assertN14("N-14b", source, N14_SOURCE_OPERATION_HEAD_OFFSET);
    assertN14("N-14c", source, N14_BOOK_ID_OFFSET);
    assertN14("N-14d", source, N14_TRAILER_SNAPSHOT_LENGTH_OFFSET);
  }

  @Test
  void executesTheBackupArtifactAndReceiptFromTheirDeclaredRawResources() {
    AttestationCorpusResources.Artifact artifact = AttestationStaticCorpusVectors.artifactB05();
    assertDoesNotThrow(
        () ->
            AttestationArtifactVerifier.verifyArtifact(
                artifact.encoded(), source -> AttestationStaticCorpusVectors.book("B-02").decode()),
        artifact.id());
    AttestationStaticCorpusVectors.requirePolicyFold("B-02", BACKUP_AT_SOURCE_ORDER_THREE);

    AttestationCorpusResources.Receipt receipt = AttestationStaticCorpusVectors.receiptB11();
    AttestationBookVerification verification =
        AttestationBookVerifier.verify(receipt.book().decode());
    assertDoesNotThrow(
        () ->
            AttestationArtifactVerifier.verifyReceipt(
                receipt.encoded(), verification, AttestationReceiptRetention.INDEPENDENT),
        receipt.id());
    AttestationStaticCorpusVectors.requirePolicyFold("B-02", ANCHOR_AT_ORDER_THREE);
  }

  @Test
  void executesTheStandaloneEnvelopeResourcesFromTheirDeclaredRawResources() {
    assertStandalone(AttestationStaticCorpusVectors.b08());
    assertStandalone(AttestationStaticCorpusVectors.b09());
  }

  private static void assertRestoreTarget(String id) {
    AttestationCorpusResources.Book target = AttestationStaticCorpusVectors.book(id);
    AttestationBookVerification verification =
        assertDoesNotThrow(() -> AttestationBookVerifier.verify(target.decode()), id);
    assertEquals(BigInteger.valueOf(4), verification.headOrder(), id);
    assertEquals(
        AttestationOperationKind.RESTORE_BOOK.wireToken(),
        target.operations().get(4).envelope().payload().operationKind(),
        id);
  }

  private static void assertN14(String id, byte[] source, int offset) {
    AttestationStaticCorpus.Fixture fixture =
        AttestationStaticCorpus.fixture(
            id,
            source,
            AttestationStaticCorpus.Mutation.replace(
                offset, new byte[] {(byte) (source[offset] ^ 1)}),
            BACKUP_AT_SOURCE_ORDER_THREE,
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

  private static void assertStandalone(AttestationStaticCorpusVectors.StandaloneEnvelope fixture) {
    fixture.policy().requireMatches(fixture.registry());
    assertDoesNotThrow(
        () ->
            AttestationAuthorization.requireAuthorized(
                fixture.registry(), fixture.context(), fixture.envelope()),
        fixture.id());
  }
}
