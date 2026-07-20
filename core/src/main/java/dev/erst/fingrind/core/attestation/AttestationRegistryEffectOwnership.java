package dev.erst.fingrind.core.attestation;

/** Enforces the closed operation-family ownership of authority-changing effect facts. */
final class AttestationRegistryEffectOwnership {
  private AttestationRegistryEffectOwnership() {}

  /** Rejects registry effects that do not belong to the attested operation kind. */
  static void require(
      AttestationOperationKind operationKind, AttestationRegistryEffectSets effects) {
    switch (operationKind) {
      case ENROLL_KEY -> requireBinding(effects, "enroll");
      case ROLLOVER_KEY -> requireBinding(effects, "rollover");
      case REVOKE_KEY -> requireRevocation(effects);
      case ALTER_POLICY -> requirePolicyChange(effects);
      default -> requireNoRegistryEffects(effects);
    }
  }

  private static void requireBinding(AttestationRegistryEffectSets effects, String action) {
    if (effects.bindings().isEmpty() || hasNonBindingEffects(effects)) {
      throw failure();
    }
    for (AttestationPreimage.Fact binding : effects.bindings()) {
      if (!action.equals(token(binding, 3))) {
        throw failure();
      }
    }
  }

  private static void requireRevocation(AttestationRegistryEffectSets effects) {
    if (effects.revocations().isEmpty()
        || !effects.bindings().isEmpty()
        || hasPolicyEffects(effects)) {
      throw failure();
    }
  }

  private static void requirePolicyChange(AttestationRegistryEffectSets effects) {
    if (!effects.bindings().isEmpty()
        || !effects.revocations().isEmpty()
        || !hasPolicyEffects(effects)) {
      throw failure();
    }
  }

  private static void requireNoRegistryEffects(AttestationRegistryEffectSets effects) {
    if (!effects.bindings().isEmpty()
        || !effects.revocations().isEmpty()
        || hasPolicyEffects(effects)) {
      throw failure();
    }
  }

  private static boolean hasNonBindingEffects(AttestationRegistryEffectSets effects) {
    return !effects.revocations().isEmpty() || hasPolicyEffects(effects);
  }

  private static boolean hasPolicyEffects(AttestationRegistryEffectSets effects) {
    return !effects.grants().isEmpty()
        || !effects.policyRules().isEmpty()
        || !effects.workflowPolicies().isEmpty();
  }

  private static String token(AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationPreimageValueReader.token(
        fact, fieldIndex, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }

  private static AttestationAuthorizationException failure() {
    return new AttestationAuthorizationException(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }
}
