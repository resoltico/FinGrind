package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Shared deterministic builders and assertions for attestation authorization tests. */
final class AttestationAuthorizationTestSupport {
  static final byte[] PAYLOAD = new byte[] {1, 2, 3};
  static final BigInteger POSITION = BigInteger.ONE;

  private AttestationAuthorizationTestSupport() {}

  static void authorize(AttestationRegistry registry, AttestationAuthorizationEnvelope envelope) {
    AttestationAuthorization.requireAuthorized(
        registry,
        POSITION,
        AttestationAuthorizationScope.operation(
            AttestationOperationKind.POST_ENTRY, AttestationSourceChannel.CLI),
        envelope);
  }

  static AttestationRegistry registry(
      List<TestCredential> credentials,
      List<AttestationCredentialRevocation> revocations,
      List<AttestationPolicyRule> policyRules) {
    List<AttestationCredentialBinding> bindings =
        credentials.stream().map(credential -> binding(0, credential)).toList();
    List<AttestationCapabilityGrant> grants = new ArrayList<>();
    for (TestCredential credential : credentials) {
      grants.addAll(allFounderGrants(credential.principalId()));
    }
    return AttestationRegistry.of(bindings, revocations, grants, policyRules, List.of());
  }

  static List<AttestationCapabilityGrant> allFounderGrants(UUID principalId) {
    List<AttestationCapabilityGrant> grants = new ArrayList<>();
    for (AttestationCapability capability : AttestationCapability.values()) {
      grants.add(
          new AttestationCapabilityGrant(
              BigInteger.ZERO, principalId, capability, AttestationGrantState.GRANT));
    }
    return grants;
  }

  static List<AttestationPolicyRule> defaultRules(int founderCount) {
    return java.util.Arrays.stream(AttestationCapability.values())
        .map(
            capability ->
                new AttestationPolicyRule(
                    BigInteger.ZERO, capability, capability.genesisQuorum(founderCount)))
        .toList();
  }

  static AttestationPolicyRule postRule(int order, int quorum) {
    return new AttestationPolicyRule(BigInteger.valueOf(order), AttestationCapability.POST, quorum);
  }

  static AttestationCredentialBinding binding(int order, TestCredential credential) {
    return binding(order, credential, AttestationCredentialPurpose.OPERATOR);
  }

  static AttestationCredentialBinding binding(
      int order, TestCredential credential, AttestationCredentialPurpose purpose) {
    return new AttestationCredentialBinding(
        BigInteger.valueOf(order),
        credential.principalId(),
        credential.keyId(),
        AttestationCredentialBinding.BindingAction.ENROLL,
        AttestationSpki.of(credential.pair().getPublic().getEncoded()),
        purpose,
        null);
  }

  static AttestationCredentialBinding rollover(
      int order, TestCredential credential, AttestationHash predecessorKeyId) {
    return new AttestationCredentialBinding(
        BigInteger.valueOf(order),
        credential.principalId(),
        credential.keyId(),
        AttestationCredentialBinding.BindingAction.ROLLOVER,
        AttestationSpki.of(credential.pair().getPublic().getEncoded()),
        AttestationCredentialPurpose.OPERATOR,
        predecessorKeyId);
  }

  static AttestationFounder founder(TestCredential credential) {
    return new AttestationFounder(
        credential.principalId(),
        credential.keyId(),
        AttestationSpki.of(credential.pair().getPublic().getEncoded()));
  }

  static AttestationAuthorizationEnvelope signedEnvelope(TestCredential... credentials) {
    return new AttestationAuthorizationEnvelope(PAYLOAD, orderedEntries(credentials));
  }

  static List<AttestationSignatureEntry> orderedEntries(TestCredential... credentials) {
    return java.util.Arrays.stream(credentials)
        .map(
            credential ->
                new AttestationSignatureEntry(
                    credential.principalId(),
                    credential.keyId(),
                    AttestationEd25519.sign(credential.pair().getPrivate(), PAYLOAD)))
        .sorted(Comparator.comparing(AttestationSignatureEntry::keyId))
        .toList();
  }

  static TestCredential credential() {
    KeyPair pair = AttestationEd25519.generateKeyPair();
    return new TestCredential(UUID.randomUUID(), pair, AttestationEd25519.keyId(pair.getPublic()));
  }

  static void assertFailure(AttestationAuthorizationFailure expected, Runnable action) {
    assertEquals(
        expected, assertThrows(AttestationAuthorizationException.class, action::run).failure());
  }
}

record TestCredential(UUID principalId, KeyPair pair, AttestationHash keyId) {}
