package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.List;

/** Decodes the registry facts that an accepted immutable operation contributes to its history. */
final class AttestationRegistryEffectDecoder {
  private AttestationRegistryEffectDecoder() {}

  static DecodedFacts decode(
      AttestationOperationKind operationKind,
      BigInteger operationOrder,
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage) {
    BigInteger checkedOrder =
        AttestationUnsignedEncoding.requireUnsigned(operationOrder, Long.BYTES, "operationOrder");
    AttestationRegistryEffectSets effects = AttestationRegistryEffectSets.from(effectPreimage);
    AttestationRegistryEffectOwnership.require(operationKind, effects);
    AttestationRegistryEffectProjection.require(requestPreimage, effects);
    return AttestationRegistryEffectFactDecoder.decode(checkedOrder, effects);
  }

  record DecodedFacts(
      List<AttestationCredentialBinding> bindings,
      List<AttestationCredentialRevocation> revocations,
      List<AttestationCapabilityGrant> grants,
      List<AttestationPolicyRule> policyRules,
      List<AttestationSystemWorkflowPolicy> workflowPolicies) {}
}
