package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    return bindingByKey;
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
