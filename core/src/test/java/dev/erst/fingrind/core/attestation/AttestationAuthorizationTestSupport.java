package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.security.KeyPair;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Shared deterministic builders and assertions for attestation authorization tests. */
final class AttestationAuthorizationTestSupport {
  static final BigInteger POSITION = BigInteger.ONE;
  private static final UUID BOOK_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
  private static final AttestationHash PREVIOUS_HEAD = AttestationHash.sha256(new byte[] {1});
  private static final AttestationHash REQUEST_DIGEST = AttestationHash.sha256(new byte[] {2});
  private static final AttestationHash EFFECT_DIGEST = AttestationHash.sha256(new byte[] {3});
  private static final AttestationAuthorizationContext OPERATION_CONTEXT = operationContext();
  static final byte[] PAYLOAD = OPERATION_CONTEXT.payload();

  private AttestationAuthorizationTestSupport() {}

  static void authorize(AttestationRegistry registry, AttestationAuthorizationEnvelope envelope) {
    AttestationAuthorization.requireAuthorized(registry, OPERATION_CONTEXT, envelope);
  }

  static AttestationAuthorizationContext operationContext() {
    return operationContext(AttestationOperationKind.POST_ENTRY, AttestationSourceChannel.CLI);
  }

  static AttestationAuthorizationContext operationContext(
      AttestationOperationKind operationKind, AttestationSourceChannel sourceChannel) {
    return operationContext(POSITION.add(BigInteger.ONE), operationKind, sourceChannel);
  }

  static AttestationAuthorizationContext operationContext(
      BigInteger operationOrder,
      AttestationOperationKind operationKind,
      AttestationSourceChannel sourceChannel) {
    return AttestationAuthorizationContext.operation(
        new AttestationOperationPayload(
            BOOK_ID,
            operationOrder,
            operationKind.wireToken(),
            PREVIOUS_HEAD,
            Instant.parse("2026-07-20T00:00:00Z"),
            REQUEST_DIGEST,
            EFFECT_DIGEST),
        sourceChannel);
  }

  static AttestationAuthorizationContext manifestContext() {
    return AttestationAuthorizationContext.manifest(
        new AttestationBackupManifestPayload(
            BOOK_ID,
            UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100"),
            POSITION,
            PREVIOUS_HEAD,
            REQUEST_DIGEST));
  }

  static AttestationAuthorizationContext receiptContext() {
    return AttestationAuthorizationContext.receipt(
        new AttestationReceiptPayload(
            BOOK_ID, POSITION, PREVIOUS_HEAD, Instant.parse("2026-07-20T00:00:00Z")));
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
    return AttestationRegistry.fromVerifierFacts(
        bindings, revocations, grants, policyRules, List.of());
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

  static AttestationSystemWorkflowPolicy interimWorkflow(
      int order, UUID workflowId, boolean active) {
    return new AttestationSystemWorkflowPolicy(
        BigInteger.valueOf(order),
        workflowId,
        AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP,
        "3000",
        null,
        null,
        active);
  }

  static AttestationSystemWorkflowPolicy fiscalWorkflow(
      int order, UUID workflowId, boolean active) {
    return new AttestationSystemWorkflowPolicy(
        BigInteger.valueOf(order),
        workflowId,
        AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE,
        "3000",
        "3100",
        "3200",
        active);
  }

  static AttestationAuthorizationEnvelope signedEnvelope(TestCredential... credentials) {
    return new AttestationAuthorizationEnvelope(PAYLOAD, orderedEntries(credentials));
  }

  static AttestationAuthorizationEnvelope signedEnvelope(
      AttestationAuthorizationContext context, TestCredential... credentials) {
    byte[] payload = context.payload();
    return new AttestationAuthorizationEnvelope(payload, orderedEntries(payload, credentials));
  }

  static List<AttestationSignatureEntry> orderedEntries(TestCredential... credentials) {
    return orderedEntries(PAYLOAD, credentials);
  }

  static List<AttestationSignatureEntry> orderedEntries(
      byte[] payload, TestCredential... credentials) {
    return java.util.Arrays.stream(credentials)
        .map(
            credential ->
                new AttestationSignatureEntry(
                    credential.principalId(),
                    credential.keyId(),
                    AttestationEd25519.sign(credential.pair().getPrivate(), payload)))
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
