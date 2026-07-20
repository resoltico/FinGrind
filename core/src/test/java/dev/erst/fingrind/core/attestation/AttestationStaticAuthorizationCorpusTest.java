package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.binding;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.interimWorkflow;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.operationContext;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.signedEnvelope;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Executes the authorization-layer rows in the immutable Slice 4 corpus. */
class AttestationStaticAuthorizationCorpusTest {
  @Test
  void executesTheByteAddressedCredentialPurposeAndGrantRowsN16AndN18() {
    TestCredential c = credential();
    AttestationAuthorizationContext systemClose =
        operationContext(
            AttestationOperationKind.INTERIM_RESULT_SWEEP, AttestationSourceChannel.SYSTEM);
    AttestationRegistry operatorC =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(0, c, AttestationCredentialPurpose.OPERATOR)),
            List.of(),
            List.of(
                new AttestationCapabilityGrant(
                    BigInteger.ZERO,
                    c.principalId(),
                    AttestationCapability.CLOSE_PERIOD,
                    AttestationGrantState.GRANT)),
            List.of(
                new AttestationPolicyRule(BigInteger.ZERO, AttestationCapability.CLOSE_PERIOD, 1)),
            List.of(
                interimWorkflow(0, AttestationAuthorizationTestSupport.SYSTEM_WORKFLOW_ID, true)));
    AttestationPreimage n16Source =
        AttestationPreimage.of(
            List.of(
                new AttestationPreimage.Fact(
                    0x0002,
                    List.of(
                        AttestationField.present(AttestationNumericFieldValue.mutation(0)),
                        AttestationField.present(AttestationBinaryFieldValue.uuid(c.principalId())),
                        AttestationField.present(AttestationBinaryFieldValue.hash(c.keyId())),
                        AttestationField.present(AttestationTextFieldValue.token("enroll")),
                        AttestationField.present(
                            AttestationBinaryFieldValue.spki(c.pair().getPublic().getEncoded())),
                        AttestationField.present(AttestationTextFieldValue.token("operator")),
                        AttestationField.absent()))));
    AttestationStaticCorpus.Fixture n16 =
        fixture(
            "N-16",
            n16Source.encoded(),
            "operator".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
            "CLOSE_PERIOD M=1 with a system-derived operation",
            AttestationAuthorizationFailure.CREDENTIAL_PURPOSE_INVALID);
    assertFixtureFailure(
        n16,
        () ->
            AttestationAuthorization.requireAuthorized(
                operatorC, systemClose, signedEnvelope(systemClose, c)));

    TestCredential a = credential();
    TestCredential b = credential();
    AttestationRegistry cWithoutPostGrant =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(0, a), binding(0, b), binding(0, c)),
            List.of(),
            List.of(
                new AttestationCapabilityGrant(
                    BigInteger.ZERO,
                    a.principalId(),
                    AttestationCapability.POST,
                    AttestationGrantState.GRANT),
                new AttestationCapabilityGrant(
                    BigInteger.ZERO,
                    b.principalId(),
                    AttestationCapability.POST,
                    AttestationGrantState.GRANT)),
            List.of(new AttestationPolicyRule(BigInteger.ZERO, AttestationCapability.POST, 2)),
            List.of());
    AttestationAuthorizationEnvelope n18Envelope = signedEnvelope(b, c);
    AttestationStaticCorpus.Fixture n18 =
        fixture(
            "N-18",
            AttestationStaticCorpus.rawEnvelope(n18Envelope),
            principalBytes(c.principalId()),
            "POST M=2 with grants only for A and B",
            AttestationAuthorizationFailure.CAPABILITY_INVALID);
    assertFixtureFailure(
        n18,
        () ->
            AttestationAuthorization.requireAuthorized(
                cWithoutPostGrant, operationContext(), n18Envelope));
  }

  private static AttestationStaticCorpus.Fixture fixture(
      String id,
      byte[] rawSource,
      byte[] replacementBytes,
      String policyFold,
      AttestationAuthorizationFailure expectedFirstFailure) {
    return AttestationStaticCorpus.fixture(
        id,
        rawSource,
        AttestationStaticCorpus.Mutation.replace(
            indexOf(rawSource, replacementBytes), replacementBytes),
        new AttestationStaticCorpus.PolicyFold(policyFold),
        AttestationStaticCorpus.VerificationScope.AUTHORIZATION,
        expectedFirstFailure);
  }

  private static void assertFixtureFailure(
      AttestationStaticCorpus.Fixture fixture, Runnable verification) {
    assertTrue(fixture.source().length > 0);
    assertFailure(fixture.expectedFirstFailure(), verification);
  }

  private static byte[] principalBytes(UUID principalId) {
    return java.nio.ByteBuffer.allocate(16)
        .putLong(principalId.getMostSignificantBits())
        .putLong(principalId.getLeastSignificantBits())
        .array();
  }

  private static int indexOf(byte[] source, byte[] target) {
    for (int offset = 0; offset <= source.length - target.length; offset++) {
      if (java.util.Arrays.equals(
          target, java.util.Arrays.copyOfRange(source, offset, offset + target.length))) {
        return offset;
      }
    }
    throw new IllegalArgumentException("Fixture mutation bytes are not present in the raw source.");
  }
}
