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
}
