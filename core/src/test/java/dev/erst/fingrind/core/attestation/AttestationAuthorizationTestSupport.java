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
import org.jspecify.annotations.Nullable;

/** Shared deterministic builders and assertions for attestation authorization tests. */
final class AttestationAuthorizationTestSupport {
  static final BigInteger POSITION = BigInteger.ONE;
  static final UUID SYSTEM_WORKFLOW_ID = UUID.fromString("99887766-5544-3322-1100-ffeeddccbbaa");
  static final UUID BOOK_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
  private static final AttestationHash PREVIOUS_HEAD = AttestationHash.sha256(new byte[] {1});
  private static final AttestationHash SNAPSHOT_DIGEST = AttestationHash.sha256(new byte[] {2});
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
    return operationContext(
        POSITION.add(BigInteger.ONE),
        operationKind,
        sourceChannel,
        sourceChannel == AttestationSourceChannel.SYSTEM ? SYSTEM_WORKFLOW_ID : null);
  }

  static AttestationAuthorizationContext operationContext(
      BigInteger operationOrder,
      AttestationOperationKind operationKind,
      AttestationSourceChannel sourceChannel) {
    return operationContext(
        operationOrder,
        operationKind,
        sourceChannel,
        sourceChannel == AttestationSourceChannel.SYSTEM ? SYSTEM_WORKFLOW_ID : null);
  }

  static AttestationAuthorizationContext operationContext(
      BigInteger operationOrder,
      AttestationOperationKind operationKind,
      AttestationSourceChannel sourceChannel,
      @Nullable UUID systemWorkflowId) {
    AttestationPreimage requestPreimage =
        requestPreimage(operationKind, sourceChannel, systemWorkflowId);
    AttestationOperationPayload payload =
        operationPayload(operationOrder, operationKind, requestPreimage);
    return AttestationAuthorizationContext.operation(
        payload, AttestationVerifiedOperationProvenance.verify(payload, requestPreimage));
  }

  static AttestationOperationPayload operationPayload(
      BigInteger operationOrder,
      AttestationOperationKind operationKind,
      AttestationPreimage requestPreimage) {
    return new AttestationOperationPayload(
        BOOK_ID,
        operationOrder,
        operationKind.wireToken(),
        PREVIOUS_HEAD,
        Instant.parse("2026-07-20T00:00:00Z"),
        AttestationHash.sha256(requestPreimage.encoded()),
        EFFECT_DIGEST);
  }

  static AttestationAuthorizationContext manifestContext() {
    return AttestationAuthorizationContext.manifest(
        new AttestationBackupManifestPayload(
            BOOK_ID,
            UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100"),
            POSITION,
            PREVIOUS_HEAD,
            SNAPSHOT_DIGEST));
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

  static AttestationPreimage requestPreimage(
      AttestationOperationKind operationKind,
      AttestationSourceChannel sourceChannel,
      @Nullable UUID systemWorkflowId) {
    List<AttestationPreimage.Fact> records = new ArrayList<>();
    records.add(command(operationKind, sourceChannel));
    if (sourceChannel == AttestationSourceChannel.SYSTEM && systemWorkflowId != null) {
      records.add(
          new AttestationPreimage.Fact(
              0x0141,
              List.of(
                  AttestationField.present(AttestationBinaryFieldValue.uuid(systemWorkflowId)))));
    }
    return AttestationPreimage.of(records);
  }

  private static AttestationPreimage.Fact command(
      AttestationOperationKind operationKind, AttestationSourceChannel sourceChannel) {
    return new AttestationPreimage.Fact(
        0x0100,
        List.of(
            AttestationField.present(AttestationTextFieldValue.token(operationKind.wireToken())),
            AttestationField.absent(),
            AttestationField.absent(),
            AttestationField.present(AttestationTextFieldValue.token(sourceChannel.wireToken()))));
  }
}

record TestCredential(UUID principalId, KeyPair pair, AttestationHash keyId) {}
