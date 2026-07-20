package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.binding;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.interimWorkflow;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.operationContext;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.signedEnvelope;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises authorization edge conditions independently of complete-book corpus execution. */
class AttestationStaticAuthorizationCorpusTest {
  @Test
  void rejectsAnOperatorForSystemWorkAndAnUngrantedSignerForPosting() {
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
    assertFailure(
        AttestationAuthorizationFailure.CREDENTIAL_PURPOSE_INVALID,
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
    assertFailure(
        AttestationAuthorizationFailure.CAPABILITY_INVALID,
        () ->
            AttestationAuthorization.requireAuthorized(
                cWithoutPostGrant, operationContext(), signedEnvelope(b, c)));
  }
}
