package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;

/** Translates already-proven registry effect facts into immutable authority history values. */
final class AttestationRegistryEffectFactDecoder {
  private AttestationRegistryEffectFactDecoder() {}

  /** Decodes all projected registry effect groups at one unsigned operation order. */
  static AttestationRegistryEffectDecoder.DecodedFacts decode(
      BigInteger operationOrder, AttestationRegistryEffectSets effects) {
    return new AttestationRegistryEffectDecoder.DecodedFacts(
        effects.bindings().stream().map(fact -> binding(operationOrder, fact)).toList(),
        effects.retirements().stream().map(fact -> retirement(operationOrder, fact)).toList(),
        effects.grants().stream().map(fact -> grant(operationOrder, fact)).toList(),
        effects.policyRules().stream().map(fact -> policy(operationOrder, fact)).toList(),
        effects.workflowPolicies().stream().map(fact -> workflow(operationOrder, fact)).toList());
  }

  private static AttestationCredentialBinding binding(
      BigInteger operationOrder, AttestationPreimage.Fact fact) {
    AttestationCredentialBinding.BindingAction action =
        switch (AttestationPreimageValueReader.token(
            fact, 3, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID)) {
          case "enroll" -> AttestationCredentialBinding.BindingAction.ENROLL;
          case "rollover" -> AttestationCredentialBinding.BindingAction.ROLLOVER;
          default -> throw failure();
        };
    AttestationCredentialPurpose purpose =
        switch (AttestationPreimageValueReader.token(
            fact, 5, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID)) {
          case "operator" -> AttestationCredentialPurpose.OPERATOR;
          case "system" -> AttestationCredentialPurpose.SYSTEM;
          default -> throw failure();
        };
    return new AttestationCredentialBinding(
        operationOrder,
        AttestationPreimageValueReader.uuid(
            fact, 1, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        AttestationPreimageValueReader.hash(
            fact, 2, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        action,
        AttestationPreimageValueReader.spki(
            fact, 4, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        purpose,
        AttestationPreimageValueReader.optionalHash(
            fact, 6, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID));
  }

  private static AttestationCredentialRetirement retirement(
      BigInteger operationOrder, AttestationPreimage.Fact fact) {
    AttestationCredentialRetirementState state =
        switch (AttestationPreimageValueReader.token(
            fact, 3, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID)) {
          case "superseded" -> AttestationCredentialRetirementState.SUPERSEDED;
          case "revoked" -> AttestationCredentialRetirementState.REVOKED;
          default -> throw failure();
        };
    return new AttestationCredentialRetirement(
        operationOrder,
        AttestationPreimageValueReader.uuid(
            fact, 2, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        AttestationPreimageValueReader.hash(
            fact, 1, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        state);
  }

  private static AttestationCapabilityGrant grant(
      BigInteger operationOrder, AttestationPreimage.Fact fact) {
    AttestationGrantState state =
        switch (AttestationPreimageValueReader.token(
            fact, 3, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID)) {
          case "grant" -> AttestationGrantState.GRANT;
          case "revoke" -> AttestationGrantState.REVOKE;
          default -> throw failure();
        };
    return new AttestationCapabilityGrant(
        operationOrder,
        AttestationPreimageValueReader.uuid(
            fact, 1, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        capability(fact, 2),
        state);
  }

  private static AttestationPolicyRule policy(
      BigInteger operationOrder, AttestationPreimage.Fact fact) {
    return new AttestationPolicyRule(
        operationOrder,
        capability(fact, 1),
        AttestationPreimageValueReader.unsigned16(
            fact, 2, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID));
  }

  private static AttestationSystemWorkflowPolicy workflow(
      BigInteger operationOrder, AttestationPreimage.Fact fact) {
    return new AttestationSystemWorkflowPolicy(
        operationOrder,
        AttestationPreimageValueReader.uuid(
            fact, 1, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        workflowKind(fact, 2),
        AttestationPreimageValueReader.text(
            fact, 3, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        AttestationPreimageValueReader.optionalText(
            fact, 4, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        AttestationPreimageValueReader.optionalText(
            fact, 5, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        AttestationPreimageValueReader.booleanValue(
            fact, 6, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID));
  }

  private static AttestationCapability capability(AttestationPreimage.Fact fact, int fieldIndex) {
    String token =
        AttestationPreimageValueReader.token(
            fact, fieldIndex, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
    for (AttestationCapability capability : AttestationCapability.values()) {
      if (capability.token().equals(token)) {
        return capability;
      }
    }
    throw failure();
  }

  private static AttestationSystemWorkflowKind workflowKind(
      AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationSystemWorkflowKind.forWireToken(
        AttestationPreimageValueReader.token(
            fact, fieldIndex, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }

  private static AttestationAuthorizationException failure() {
    return new AttestationAuthorizationException(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }
}
