package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Projects one resolved registry head into the stable inspection read model. */
final class AttestationRegistryInspectionAssembler {
  private AttestationRegistryInspectionAssembler() {}

  static AttestationRegistryInspection assemble(
      AttestationRegistryResolution resolution,
      List<AttestationCredentialBinding> bindings,
      List<AttestationSystemWorkflowPolicy> workflowPolicies,
      UUID bookId,
      BigInteger headOrder,
      String operationHeadHex) {
    List<AttestationRegistryInspection.Credential> credentials =
        bindings.stream()
            .sorted(
                Comparator.comparing(AttestationCredentialBinding::principalId)
                    .thenComparing(AttestationCredentialBinding::acceptedOrder)
                    .thenComparing(AttestationCredentialBinding::keyId))
            .map(binding -> credentialInspection(resolution, binding, headOrder))
            .toList();
    List<UUID> principals =
        bindings.stream()
            .map(AttestationCredentialBinding::principalId)
            .distinct()
            .sorted()
            .toList();
    List<AttestationRegistryInspection.CapabilityPolicy> policies =
        java.util.Arrays.stream(AttestationCapability.values())
            .map(capability -> capabilityPolicyInspection(resolution, capability, headOrder))
            .toList();
    List<AttestationRegistryInspection.PrincipalCapability> principalCapabilities =
        principals.stream()
            .flatMap(
                principalId ->
                    java.util.Arrays.stream(AttestationCapability.values())
                        .map(
                            capability ->
                                new AttestationRegistryInspection.PrincipalCapability(
                                    principalId,
                                    capability.token(),
                                    resolution.isEligible(principalId, capability, headOrder))))
            .toList();
    List<AttestationRegistryInspection.SystemWorkflowPolicy> workflows =
        workflowPolicies.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    AttestationSystemWorkflowPolicy::workflowId,
                    policy -> policy,
                    java.util.function.BinaryOperator.maxBy(
                        Comparator.comparing(AttestationSystemWorkflowPolicy::acceptedOrder))))
            .values()
            .stream()
            .sorted(Comparator.comparing(AttestationSystemWorkflowPolicy::workflowId))
            .map(AttestationRegistryInspectionAssembler::workflowPolicyInspection)
            .toList();
    return new AttestationRegistryInspection(
        bookId,
        headOrder,
        operationHeadHex,
        credentials,
        policies,
        principalCapabilities,
        workflows);
  }

  private static AttestationRegistryInspection.Credential credentialInspection(
      AttestationRegistryResolution resolution,
      AttestationCredentialBinding binding,
      BigInteger headOrder) {
    AttestationCredentialState state = resolution.credentialAt(binding.keyId(), headOrder);
    return new AttestationRegistryInspection.Credential(
        binding.principalId(),
        binding.keyId().hex(),
        Base64.getUrlEncoder().withoutPadding().encodeToString(binding.spki().bytes()),
        binding.purpose().token(),
        binding.action().name().toLowerCase(java.util.Locale.ROOT),
        binding.acceptedOrder(),
        binding.predecessorKeyId() == null ? null : binding.predecessorKeyId().hex(),
        state.active() ? "active" : "revoked");
  }

  private static AttestationRegistryInspection.CapabilityPolicy capabilityPolicyInspection(
      AttestationRegistryResolution resolution,
      AttestationCapability capability,
      BigInteger headOrder) {
    return new AttestationRegistryInspection.CapabilityPolicy(
        capability.token(),
        resolution.quorumAt(capability, headOrder),
        resolution.eligiblePrincipalCount(capability, headOrder, null),
        resolution.eligiblePrincipalCount(
            capability, headOrder, AttestationCredentialPurpose.OPERATOR),
        resolution.eligiblePrincipalCount(
            capability, headOrder, AttestationCredentialPurpose.SYSTEM));
  }

  private static AttestationRegistryInspection.SystemWorkflowPolicy workflowPolicyInspection(
      AttestationSystemWorkflowPolicy policy) {
    return new AttestationRegistryInspection.SystemWorkflowPolicy(
        policy.workflowId(),
        policy.workflowKind().wireToken(),
        policy.resultHoldingAccountCode(),
        policy.capitalAccountCode(),
        policy.retainedResultAccountCode(),
        policy.active(),
        policy.acceptedOrder());
  }
}
