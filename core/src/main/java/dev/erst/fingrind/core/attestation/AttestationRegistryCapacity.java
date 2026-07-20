package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;

/** Rejects history states that would make a configured quorum unreachable. */
final class AttestationRegistryCapacity {
  private AttestationRegistryCapacity() {}

  static void requireCapacityAt(
      AttestationRegistryResolution resolution, BigInteger resolvingOrder) {
    for (AttestationCapability capability : AttestationCapability.values()) {
      if (!resolution.hasPolicyAt(capability, resolvingOrder)) {
        continue;
      }
      int quorum = resolution.quorumAt(capability, resolvingOrder);
      if (resolution.eligiblePrincipalCount(capability, resolvingOrder, null) < quorum) {
        throw new AttestationAuthorizationException(
            AttestationAuthorizationFailure.CAPABILITY_INVALID);
      }
      if (resolution.eligiblePrincipalCount(
              capability, resolvingOrder, AttestationCredentialPurpose.OPERATOR)
          < quorum) {
        throw new AttestationAuthorizationException(
            AttestationAuthorizationFailure.CAPABILITY_INVALID);
      }
      if (capability == AttestationCapability.CLOSE_PERIOD
          && resolution.hasActiveSystemWorkflowAt(resolvingOrder)
          && resolution.eligiblePrincipalCount(
                  capability, resolvingOrder, AttestationCredentialPurpose.SYSTEM)
              < quorum) {
        throw new AttestationAuthorizationException(
            AttestationAuthorizationFailure.CAPABILITY_INVALID);
      }
    }
  }
}
