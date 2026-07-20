package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Historical query engine for immutable attestation registry facts. */
final class AttestationRegistryResolution {
  private final List<AttestationCredentialBinding> bindings;
  private final List<AttestationCredentialRevocation> revocations;
  private final List<AttestationCapabilityGrant> grants;
  private final List<AttestationPolicyRule> policyRules;
  private final List<AttestationSystemWorkflowPolicy> workflowPolicies;
  private final Map<AttestationHash, AttestationCredentialBinding> bindingByKey;

  AttestationRegistryResolution(
      List<AttestationCredentialBinding> bindings,
      List<AttestationCredentialRevocation> revocations,
      List<AttestationCapabilityGrant> grants,
      List<AttestationPolicyRule> policyRules,
      List<AttestationSystemWorkflowPolicy> workflowPolicies,
      Map<AttestationHash, AttestationCredentialBinding> bindingByKey) {
    this.bindings = List.copyOf(bindings);
    this.revocations = List.copyOf(revocations);
    this.grants = List.copyOf(grants);
    this.policyRules = List.copyOf(policyRules);
    this.workflowPolicies = List.copyOf(workflowPolicies);
    this.bindingByKey = Map.copyOf(bindingByKey);
  }

  int quorumAt(AttestationCapability capability, BigInteger resolvingOrder) {
    AttestationCapability checkedCapability = Objects.requireNonNull(capability, "capability");
    BigInteger checkedOrder = Objects.requireNonNull(resolvingOrder, "resolvingOrder");
    return latestPolicy(checkedCapability, checkedOrder)
        .map(AttestationPolicyRule::quorum)
        .orElseThrow(
            () ->
                new AttestationAuthorizationException(
                    AttestationAuthorizationFailure.CAPABILITY_INVALID));
  }

  AttestationCredentialState credentialAt(AttestationHash keyId, BigInteger resolvingOrder) {
    AttestationHash checkedKeyId = Objects.requireNonNull(keyId, "keyId");
    BigInteger checkedOrder = Objects.requireNonNull(resolvingOrder, "resolvingOrder");
    AttestationCredentialBinding binding = bindingByKey.get(checkedKeyId);
    if (binding == null || binding.acceptedOrder().compareTo(checkedOrder) > 0) {
      return AttestationCredentialState.notEnrolled();
    }
    return isRevoked(checkedKeyId, checkedOrder)
        ? AttestationCredentialState.revoked(binding)
        : AttestationCredentialState.active(binding);
  }

  boolean isEligible(
      UUID principalId, AttestationCapability capability, BigInteger resolvingOrder) {
    UUID checkedPrincipalId = Objects.requireNonNull(principalId, "principalId");
    AttestationCapability checkedCapability = Objects.requireNonNull(capability, "capability");
    BigInteger checkedOrder = Objects.requireNonNull(resolvingOrder, "resolvingOrder");
    return bindings.stream()
            .filter(binding -> binding.principalId().equals(checkedPrincipalId))
            .anyMatch(binding -> credentialAt(binding.keyId(), checkedOrder).active())
        && latestGrant(checkedCapability, checkedOrder, checkedPrincipalId)
            .map(grant -> grant.state() == AttestationGrantState.GRANT)
            .orElse(false);
  }

  int eligiblePrincipalCount(
      AttestationCapability capability,
      BigInteger resolvingOrder,
      @Nullable AttestationCredentialPurpose credentialPurpose) {
    AttestationCapability checkedCapability = Objects.requireNonNull(capability, "capability");
    BigInteger checkedOrder = Objects.requireNonNull(resolvingOrder, "resolvingOrder");
    Set<UUID> principals = new HashSet<>();
    for (AttestationCredentialBinding binding : bindings) {
      if (credentialAt(binding.keyId(), checkedOrder).active()
          && AttestationEd25519.isEd25519Spki(binding.spki().bytes())
          && (credentialPurpose == null || binding.purpose() == credentialPurpose)) {
        principals.add(binding.principalId());
      }
    }
    return (int)
        principals.stream()
            .filter(principalId -> isEligible(principalId, checkedCapability, checkedOrder))
            .count();
  }

  boolean hasPolicyAt(AttestationCapability capability, BigInteger resolvingOrder) {
    return latestPolicy(
            Objects.requireNonNull(capability, "capability"),
            Objects.requireNonNull(resolvingOrder, "resolvingOrder"))
        .isPresent();
  }

  boolean hasActiveSystemWorkflowAt(BigInteger resolvingOrder) {
    BigInteger checkedOrder = Objects.requireNonNull(resolvingOrder, "resolvingOrder");
    Set<UUID> resolvedWorkflowIds = new HashSet<>();
    for (int index = workflowPolicies.size() - 1; index >= 0; index--) {
      AttestationSystemWorkflowPolicy policy = workflowPolicies.get(index);
      if (policy.acceptedOrder().compareTo(checkedOrder) <= 0
          && resolvedWorkflowIds.add(policy.workflowId())
          && policy.active()) {
        return true;
      }
    }
    return false;
  }

  private boolean isRevoked(AttestationHash keyId, BigInteger resolvingOrder) {
    return revocations.stream()
        .anyMatch(
            revocation ->
                revocation.keyId().equals(keyId)
                    && revocation.acceptedOrder().compareTo(resolvingOrder) <= 0);
  }

  private Optional<AttestationCapabilityGrant> latestGrant(
      AttestationCapability capability, BigInteger resolvingOrder, UUID principalId) {
    return grants.stream()
        .filter(grant -> grant.capability() == capability)
        .filter(grant -> grant.principalId().equals(principalId))
        .filter(grant -> grant.acceptedOrder().compareTo(resolvingOrder) <= 0)
        .max(Comparator.comparing(AttestationCapabilityGrant::acceptedOrder));
  }

  private Optional<AttestationPolicyRule> latestPolicy(
      AttestationCapability capability, BigInteger resolvingOrder) {
    return policyRules.stream()
        .filter(rule -> rule.capability() == capability)
        .filter(rule -> rule.acceptedOrder().compareTo(resolvingOrder) <= 0)
        .max(Comparator.comparing(AttestationPolicyRule::acceptedOrder));
  }
}
