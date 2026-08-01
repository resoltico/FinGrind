package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.allFounderGrants;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.authorize;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.binding;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.defaultRules;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.signedEnvelope;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.math.BigInteger;
import java.security.KeyPair;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Proves the terminal authorization boundary produced by a credential rollover. */
class AttestationRolloverAuthorizationTest {
  @Test
  void rolloverSupersedesItsActivePredecessorAndAdmitsOnlyTheReplacement() {
    TestCredential original = credential();
    TestCredential unrelated = credential();
    KeyPair replacementPair = AttestationEd25519.generateKeyPair();
    TestCredential replacement =
        new TestCredential(
            original.principalId(),
            replacementPair,
            AttestationEd25519.keyId(replacementPair.getPublic()));
    AttestationCredentialBinding rollover =
        new AttestationCredentialBinding(
            BigInteger.ONE,
            replacement.principalId(),
            replacement.keyId(),
            AttestationCredentialBinding.BindingAction.ROLLOVER,
            AttestationSpki.of(replacement.pair().getPublic().getEncoded()),
            AttestationCredentialPurpose.OPERATOR,
            original.keyId());
    AttestationRegistry registry =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(0, original), binding(0, unrelated), rollover),
            List.of(
                new AttestationCredentialRetirement(
                    BigInteger.ONE,
                    unrelated.principalId(),
                    unrelated.keyId(),
                    AttestationCredentialRetirementState.REVOKED),
                new AttestationCredentialRetirement(
                    BigInteger.ONE,
                    original.principalId(),
                    original.keyId(),
                    AttestationCredentialRetirementState.SUPERSEDED)),
            allFounderGrants(original.principalId()),
            defaultRules(1),
            List.of());

    assertDoesNotThrow(() -> authorize(registry, signedEnvelope(replacement)));
    assertFailure(
        AttestationAuthorizationFailure.KEY_SUPERSEDED,
        () -> authorize(registry, signedEnvelope(original)));
  }
}
