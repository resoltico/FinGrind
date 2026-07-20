package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Validates cross-fact invariants that must hold for every registry history. */
final class AttestationRegistryValidator {
  private AttestationRegistryValidator() {}

  static Map<AttestationHash, AttestationCredentialBinding> indexAndValidate(
      List<AttestationCredentialBinding> bindings,
      List<AttestationCredentialRevocation> revocations,
      List<AttestationCapabilityGrant> grants,
      List<AttestationPolicyRule> policyRules,
      List<AttestationSystemWorkflowPolicy> workflowPolicies) {
    Map<AttestationHash, AttestationCredentialBinding> bindingByKey = indexBindings(bindings);
    validateRevocations(revocations, bindingByKey);
    validateRolloverPredecessors(bindings, revocations, bindingByKey);
    validateFactUniqueness(grants, policyRules, workflowPolicies);
    validateWorkflowPolicyHistory(workflowPolicies);
    return bindingByKey;
  }

  static void requireAcceptedCredentialAlgorithms(List<AttestationCredentialBinding> bindings) {
    for (AttestationCredentialBinding binding : bindings) {
      if (!AttestationEd25519.isEd25519Spki(binding.spki().bytes())) {
        throw new AttestationAuthorizationException(
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
      }
    }
  }

  private static Map<AttestationHash, AttestationCredentialBinding> indexBindings(
      List<AttestationCredentialBinding> bindings) {
    Map<AttestationHash, AttestationCredentialBinding> result = new ConcurrentHashMap<>();
    for (AttestationCredentialBinding binding : bindings) {
      if (result.putIfAbsent(binding.keyId(), binding) != null) {
        throw new IllegalArgumentException(
            "Attestation credential keyId may occur in only one binding.");
      }
    }
    return Map.copyOf(result);
  }

  private static void validateRevocations(
      List<AttestationCredentialRevocation> revocations,
      Map<AttestationHash, AttestationCredentialBinding> bindingByKey) {
    Set<AttestationHash> revokedKeys = new HashSet<>();
    for (AttestationCredentialRevocation revocation : revocations) {
      AttestationCredentialBinding binding = bindingByKey.get(revocation.keyId());
      if (binding == null
          || !binding.principalId().equals(revocation.principalId())
          || binding.acceptedOrder().compareTo(revocation.acceptedOrder()) >= 0
          || !revokedKeys.add(revocation.keyId())) {
        throw new IllegalArgumentException(
            "Attestation credential revocation must retire one prior active binding.");
      }
    }
  }

  private static void validateRolloverPredecessors(
      List<AttestationCredentialBinding> bindings,
      List<AttestationCredentialRevocation> revocations,
      Map<AttestationHash, AttestationCredentialBinding> bindingByKey) {
    for (AttestationCredentialBinding binding : bindings) {
      if (binding.action() == AttestationCredentialBinding.BindingAction.ROLLOVER) {
        AttestationCredentialBinding predecessor = bindingByKey.get(binding.predecessorKeyId());
        BigInteger precedingOrder = binding.acceptedOrder().subtract(BigInteger.ONE);
        if (predecessor == null
            || !predecessor.principalId().equals(binding.principalId())
            || !isActiveAt(predecessor, precedingOrder, revocations)) {
          throw new IllegalArgumentException(
              "Attestation rollover predecessor must be an active credential of the same principal.");
        }
      }
    }
  }

  private static boolean isActiveAt(
      AttestationCredentialBinding binding,
      BigInteger resolvingOrder,
      List<AttestationCredentialRevocation> revocations) {
    return binding.acceptedOrder().compareTo(resolvingOrder) <= 0
        && revocations.stream()
            .noneMatch(
                revocation ->
                    revocation.keyId().equals(binding.keyId())
                        && revocation.acceptedOrder().compareTo(resolvingOrder) <= 0);
  }

  private static void validateFactUniqueness(
      List<AttestationCapabilityGrant> grants,
      List<AttestationPolicyRule> policyRules,
      List<AttestationSystemWorkflowPolicy> workflowPolicies) {
    requireUnique(
        grants,
        grant -> grant.acceptedOrder() + ":" + grant.principalId() + ":" + grant.capability());
    requireUnique(policyRules, rule -> rule.acceptedOrder() + ":" + rule.capability());
    requireUnique(workflowPolicies, policy -> policy.acceptedOrder() + ":" + policy.workflowId());
  }

  private static void validateWorkflowPolicyHistory(
      List<AttestationSystemWorkflowPolicy> workflowPolicies) {
    Map<UUID, AttestationSystemWorkflowPolicy> activeById = new ConcurrentHashMap<>();
    Set<UUID> retiredIds = new HashSet<>();
    int start = 0;
    while (start < workflowPolicies.size()) {
      int end = endOfOrder(workflowPolicies, start);
      for (int index = start; index < end; index++) {
        AttestationSystemWorkflowPolicy policy = workflowPolicies.get(index);
        if (!policy.active()) {
          retireWorkflow(policy, activeById, retiredIds);
        }
      }
      for (int index = start; index < end; index++) {
        AttestationSystemWorkflowPolicy policy = workflowPolicies.get(index);
        if (policy.active()) {
          activateWorkflow(policy, activeById, retiredIds);
        }
      }
      requireDistinctActiveWorkflowKinds(activeById.values());
      start = end;
    }
  }

  private static int endOfOrder(List<AttestationSystemWorkflowPolicy> workflowPolicies, int start) {
    BigInteger acceptedOrder = workflowPolicies.get(start).acceptedOrder();
    int index = start + 1;
    while (index < workflowPolicies.size()
        && workflowPolicies.get(index).acceptedOrder().equals(acceptedOrder)) {
      index++;
    }
    return index;
  }

  private static void retireWorkflow(
      AttestationSystemWorkflowPolicy policy,
      Map<UUID, AttestationSystemWorkflowPolicy> activeById,
      Set<UUID> retiredIds) {
    AttestationSystemWorkflowPolicy activePolicy = activeById.remove(policy.workflowId());
    if (activePolicy == null || !policy.hasSameConfiguration(activePolicy)) {
      throw new IllegalArgumentException(
          "Attestation workflow retirement must repeat one active workflow configuration.");
    }
    retiredIds.add(policy.workflowId());
  }

  private static void activateWorkflow(
      AttestationSystemWorkflowPolicy policy,
      Map<UUID, AttestationSystemWorkflowPolicy> activeById,
      Set<UUID> retiredIds) {
    if (activeById.putIfAbsent(policy.workflowId(), policy) != null
        || retiredIds.contains(policy.workflowId())) {
      throw new IllegalArgumentException(
          "Attestation workflow IDs may be activated only once and never reactivated.");
    }
  }

  private static void requireDistinctActiveWorkflowKinds(
      java.util.Collection<AttestationSystemWorkflowPolicy> activePolicies) {
    Set<AttestationSystemWorkflowKind> activeKinds =
        EnumSet.noneOf(AttestationSystemWorkflowKind.class);
    for (AttestationSystemWorkflowPolicy policy : activePolicies) {
      if (!activeKinds.add(policy.workflowKind())) {
        throw new IllegalArgumentException(
            "Attestation history may have at most one active workflow of each kind.");
      }
    }
  }

  private static <T> void requireUnique(List<T> values, Function<T, String> identity) {
    Set<String> identities = new HashSet<>();
    for (T value : values) {
      if (!identities.add(identity.apply(value))) {
        throw new IllegalArgumentException(
            "Attestation history must not repeat one fact identity at one operation order.");
      }
    }
  }
}
