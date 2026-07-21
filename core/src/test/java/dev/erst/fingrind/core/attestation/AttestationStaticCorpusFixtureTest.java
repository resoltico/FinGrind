package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

  @Test
  void executesEveryProtectedBookPositiveFromItsDeclaredRawResource() {
    for (String id : AttestationStaticCorpusVectors.positiveBookIds()) {
      AttestationStaticCorpus.Fixture fixture =
          AttestationStaticCorpusVectors.positiveBookFixture(id);
      assertScope(fixture, AttestationStaticCorpus.VerificationScope.BOOK);
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
                artifact.encoded(), AttestationStaticArtifactCorpusVectors::decodeB05Snapshot));
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
    assertScope(fixture, AttestationStaticCorpus.VerificationScope.BOOK);
    AttestationStaticCorpusVectors.requirePolicyFold(
        AttestationStaticCorpusVectors.negativePolicy("N-11"));
    assertFailure(
        fixture.expectedFirstFailure(),
        () ->
            AttestationBookVerifier.verify(
                AttestationCorpusResources.source("N-11", fixture.source()).decode()));
  }

  @Test
  void executesEveryN14ManifestNegativeAgainstTheCommittedCompleteArtifact() {
    for (String id : AttestationStaticArtifactCorpusVectors.negativeIds()) {
      AttestationStaticCorpus.Fixture fixture =
          AttestationStaticArtifactCorpusVectors.negativeFixture(id);
      assertScope(fixture, AttestationStaticCorpus.VerificationScope.ARTIFACT);
      AttestationStaticCorpusVectors.requirePolicyFold("B-02", fixture.policyFold());
      assertFailure(
          fixture.expectedFirstFailure(),
          () ->
              AttestationArtifactVerifier.verifyArtifact(
                  fixture.source(), AttestationStaticArtifactCorpusVectors::decodeB05Snapshot));
    }
  }

  @Test
  void executesTheBackupArtifactAndReceiptFromTheirDeclaredRawResources() {
    AttestationCorpusResources.Artifact artifact = AttestationStaticCorpusVectors.artifactB05();
    assertDoesNotThrow(
        () ->
            AttestationArtifactVerifier.verifyArtifact(
                artifact.encoded(), AttestationStaticArtifactCorpusVectors::decodeB05Snapshot),
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

  private static void assertScope(
      AttestationStaticCorpus.Fixture fixture, AttestationStaticCorpus.VerificationScope expected) {
    assertEquals(expected, fixture.scope(), fixture.id());
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
