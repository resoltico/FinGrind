package dev.erst.fingrind.core.attestation;

/** Enforces the closed operation-family ownership of authority-changing effect facts. */
final class AttestationRegistryEffectOwnership {
  private AttestationRegistryEffectOwnership() {}

  /** Rejects registry effects that do not belong to the attested operation kind. */
  static void require(
      AttestationOperationKind operationKind, AttestationRegistryEffectSets effects) {
    switch (operationKind) {
      case ENROLL_KEY -> requireEnrollment(effects);
      case ROLLOVER_KEY -> requireRollover(effects);
      case REVOKE_KEY -> requireRevocation(effects);
      case ALTER_POLICY -> requirePolicyChange(effects);
      default -> requireNoRegistryEffects(effects);
    }
  }

  private static void requireEnrollment(AttestationRegistryEffectSets effects) {
    if (effects.bindings().size() != 1 || hasNonBindingEffects(effects)) {
      throw failure();
    }
    if (!"enroll".equals(token(effects.bindings().getFirst(), 3))) {
      throw failure();
    }
  }

  private static void requireRollover(AttestationRegistryEffectSets effects) {
    if (effects.bindings().size() != 1
        || effects.retirements().size() != 1
        || hasPolicyEffects(effects)) {
      throw failure();
    }
    AttestationPreimage.Fact binding = effects.bindings().getFirst();
    AttestationPreimage.Fact retirement = effects.retirements().getFirst();
    if (!"rollover".equals(token(binding, 3))
        || !"superseded".equals(token(retirement, 3))
        || !java.util.Arrays.equals(
            binding.fields().get(1).encoded(), retirement.fields().get(2).encoded())
        || !java.util.Arrays.equals(
            binding.fields().get(6).encoded(), retirement.fields().get(1).encoded())) {
      throw failure();
    }
  }

  private static void requireRevocation(AttestationRegistryEffectSets effects) {
    if (effects.retirements().size() != 1
        || !effects.bindings().isEmpty()
        || hasPolicyEffects(effects)) {
      throw failure();
    }
    if (!"revoked".equals(token(effects.retirements().getFirst(), 3))) {
      throw failure();
    }
  }

  private static void requirePolicyChange(AttestationRegistryEffectSets effects) {
    if (!effects.bindings().isEmpty()
        || !effects.retirements().isEmpty()
        || !hasPolicyEffects(effects)) {
      throw failure();
    }
  }

  private static void requireNoRegistryEffects(AttestationRegistryEffectSets effects) {
    if (!effects.bindings().isEmpty()
        || !effects.retirements().isEmpty()
        || hasPolicyEffects(effects)) {
      throw failure();
    }
  }

  private static boolean hasNonBindingEffects(AttestationRegistryEffectSets effects) {
    return !effects.retirements().isEmpty() || hasPolicyEffects(effects);
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
