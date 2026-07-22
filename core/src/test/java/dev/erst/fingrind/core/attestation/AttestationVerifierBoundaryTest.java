package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisContext;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisEffectPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisPayload;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisRequestPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.signedGenesisEnvelope;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the exported raw-evidence boundary without exposing verifier internals. */
class AttestationVerifierBoundaryTest {
  @Test
  void verifiesACompleteGenesisEvidenceUnitAndDefensivelyOwnsItsBytes() {
    TestCredential founder = credential();
    AttestationPreimage request = genesisRequestPreimage(founder);
    AttestationPreimage effect = genesisEffectPreimage(founder);
    AttestationOperationPayload payload =
        genesisPayload(
            BigInteger.ZERO,
            AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]),
            request,
            effect);
    AttestationAuthorizationEnvelope authorization =
        signedGenesisEnvelope(genesisContext(founder), founder);
    byte[] envelope = AttestationEnvelope.of(payload, authorization.entries()).encoded();
    AttestationEvidence evidence =
        new AttestationEvidence(envelope, request.encoded(), effect.encoded());
    byte[] expectedEnvelope = evidence.operationEnvelope();

    envelope[0] = 0;

    AttestationVerification verification = AttestationVerifier.verifyBook(List.of(evidence));

    assertEquals(AttestationAuthorizationTestSupport.BOOK_ID, verification.bookId());
    assertEquals(BigInteger.ZERO, verification.headOrder());
    assertArrayEquals(expectedEnvelope, evidence.operationEnvelope());
  }

  @Test
  void translatesTheFirstCanonicalFailureIntoTheExportedStableCode() {
    AttestationVerificationException failure =
        assertThrows(
            AttestationVerificationException.class,
            () -> AttestationVerifier.verifyBook(List.of()));

    assertEquals("attestation-preimage-invalid", failure.code());
  }

  @Test
  void inspectsAValidChainAndAdmitsAProspectiveRegistryMutationAtItsHead() {
    TestCredential founder = credential();
    AttestationEvidence evidence = genesisEvidence(founder);

    AttestationBookInspection inspection =
        AttestationVerifier.verifyAndInspectBook(List.of(evidence));
    assertEquals(AttestationAuthorizationTestSupport.BOOK_ID, inspection.registry().bookId());
    AttestationVerifier.requireRegistryMutationAdmissible(
        List.of(evidence),
        new AttestationRegistryMutation.EnrollKey(
            java.util.UUID.randomUUID(),
            new AttestationPublicCredential(credential().pair().getPublic().getEncoded()),
            AttestationCredentialPurpose.OPERATOR));

    AttestationVerificationException invalidChain =
        assertThrows(
            AttestationVerificationException.class,
            () ->
                AttestationVerifier.requireRegistryMutationAdmissible(
                    List.of(),
                    new AttestationRegistryMutation.EnrollKey(
                        java.util.UUID.randomUUID(),
                        new AttestationPublicCredential(
                            credential().pair().getPublic().getEncoded()),
                        AttestationCredentialPurpose.OPERATOR)));
    assertEquals("attestation-preimage-invalid", invalidChain.code());
  }

  @Test
  void rejectsBookInspectionComponentsThatDoNotDescribeTheSameVerifiedHead() {
    TestCredential founder = credential();
    AttestationBookInspection inspection =
        AttestationVerifier.verifyAndInspectBook(List.of(genesisEvidence(founder)));
    AttestationVerification verification = inspection.verification();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationBookInspection(
                verification, registryInspection(java.util.UUID.randomUUID(), BigInteger.ZERO)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationBookInspection(
                verification, registryInspection(verification.bookId(), BigInteger.ONE)));
  }

  private static AttestationEvidence genesisEvidence(TestCredential founder) {
    AttestationPreimage request = genesisRequestPreimage(founder);
    AttestationPreimage effect = genesisEffectPreimage(founder);
    AttestationOperationPayload payload =
        genesisPayload(
            BigInteger.ZERO,
            AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]),
            request,
            effect);
    AttestationAuthorizationEnvelope authorization =
        signedGenesisEnvelope(genesisContext(founder), founder);
    return new AttestationEvidence(
        AttestationEnvelope.of(payload, authorization.entries()).encoded(),
        request.encoded(),
        effect.encoded());
  }

  private static AttestationRegistryInspection registryInspection(
      java.util.UUID bookId, BigInteger headOrder) {
    return new AttestationRegistryInspection(
        bookId, headOrder, "0".repeat(64), List.of(), List.of(), List.of(), List.of());
  }
}
