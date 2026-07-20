package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable append-only credential, grant, and policy history resolved at an operation position.
 */
final class AttestationRegistry {
  private final AttestationRegistryResolution resolution;

  private AttestationRegistry(
      List<AttestationCredentialBinding> bindings,
      List<AttestationCredentialRevocation> revocations,
      List<AttestationCapabilityGrant> grants,
      List<AttestationPolicyRule> policyRules,
      List<AttestationSystemWorkflowPolicy> workflowPolicies) {
    List<AttestationCredentialBinding> sortedBindings =
        AttestationRegistryFacts.sorted(bindings, AttestationCredentialBinding::acceptedOrder);
    List<AttestationCredentialRevocation> sortedRevocations =
        AttestationRegistryFacts.sorted(
            revocations, AttestationCredentialRevocation::acceptedOrder);
    List<AttestationCapabilityGrant> sortedGrants =
        AttestationRegistryFacts.sorted(grants, AttestationCapabilityGrant::acceptedOrder);
    List<AttestationPolicyRule> sortedPolicyRules =
        AttestationRegistryFacts.sorted(policyRules, AttestationPolicyRule::acceptedOrder);
    List<AttestationSystemWorkflowPolicy> sortedWorkflowPolicies =
        AttestationRegistryFacts.sorted(
            workflowPolicies, AttestationSystemWorkflowPolicy::acceptedOrder);
    resolution =
        new AttestationRegistryResolution(
            sortedBindings,
            sortedRevocations,
            sortedGrants,
            sortedPolicyRules,
            sortedWorkflowPolicies,
            AttestationRegistryValidator.indexAndValidate(
                sortedBindings,
                sortedRevocations,
                sortedGrants,
                sortedPolicyRules,
                sortedWorkflowPolicies));
  }

  /** Resolves untrusted facts so a verifier can classify their first protocol failure. */
  static AttestationRegistry fromVerifierFacts(
      List<AttestationCredentialBinding> bindings,
      List<AttestationCredentialRevocation> revocations,
      List<AttestationCapabilityGrant> grants,
      List<AttestationPolicyRule> policyRules,
      List<AttestationSystemWorkflowPolicy> workflowPolicies) {
    return new AttestationRegistry(
        Objects.requireNonNull(bindings, "bindings"),
        Objects.requireNonNull(revocations, "revocations"),
        Objects.requireNonNull(grants, "grants"),
        Objects.requireNonNull(policyRules, "policyRules"),
        Objects.requireNonNull(workflowPolicies, "workflowPolicies"));
  }

  /** Validates an accepted registry history, including every post-mutation quorum state. */
  static AttestationRegistry fromAcceptedHistory(
      List<AttestationCredentialBinding> bindings,
      List<AttestationCredentialRevocation> revocations,
      List<AttestationCapabilityGrant> grants,
      List<AttestationPolicyRule> policyRules,
      List<AttestationSystemWorkflowPolicy> workflowPolicies) {
    AttestationRegistry registry =
        fromVerifierFacts(bindings, revocations, grants, policyRules, workflowPolicies);
    AttestationRegistryValidator.requireAcceptedCredentialAlgorithms(bindings);
    AttestationRegistryCapacity.requireCapacityForAcceptedHistory(registry.resolution);
    return registry;
  }

  static AttestationRegistry genesis(List<AttestationFounder> founders) {
    List<AttestationFounder> checkedFounders =
        List.copyOf(Objects.requireNonNull(founders, "founders"));
    requireDistinctFounders(checkedFounders);
    List<AttestationCredentialBinding> bindings =
        checkedFounders.stream().map(AttestationFounder::binding).toList();
    List<AttestationCapabilityGrant> grants = new ArrayList<>();
    List<AttestationPolicyRule> policyRules = new ArrayList<>();
    for (AttestationCapability capability : AttestationCapability.values()) {
      policyRules.add(
          new AttestationPolicyRule(
              BigInteger.ZERO, capability, capability.genesisQuorum(checkedFounders.size())));
      for (AttestationFounder founder : checkedFounders) {
        grants.add(
            new AttestationCapabilityGrant(
                BigInteger.ZERO, founder.principalId(), capability, AttestationGrantState.GRANT));
      }
    }
    return fromAcceptedHistory(bindings, List.of(), grants, policyRules, List.of());
  }

  int quorumAt(AttestationCapability capability, BigInteger resolvingOrder) {
    return resolution.quorumAt(capability, resolvingOrder);
  }

  AttestationCredentialState credentialAt(AttestationHash keyId, BigInteger resolvingOrder) {
    return resolution.credentialAt(keyId, resolvingOrder);
  }

  boolean isEligible(
      UUID principalId, AttestationCapability capability, BigInteger resolvingOrder) {
    return resolution.isEligible(principalId, capability, resolvingOrder);
  }

  int eligiblePrincipalCount(AttestationCapability capability, BigInteger resolvingOrder) {
    return resolution.eligiblePrincipalCount(capability, resolvingOrder, null);
  }

  boolean hasActiveSystemWorkflow(
      AttestationSystemWorkflowKind workflowKind, BigInteger resolvingOrder) {
    return resolution.hasActiveSystemWorkflow(
        Objects.requireNonNull(workflowKind, "workflowKind"),
        Objects.requireNonNull(resolvingOrder, "resolvingOrder"));
  }

  private static void requireDistinctFounders(List<AttestationFounder> founders) {
    if (founders.isEmpty() || founders.size() > 5) {
      throw new IllegalArgumentException(
          "Attestation genesis must declare between one and five founders.");
    }
    Set<UUID> principalIds = new HashSet<>();
    Set<AttestationHash> keyIds = new HashSet<>();
    for (AttestationFounder founder : founders) {
      if (!principalIds.add(founder.principalId()) || !keyIds.add(founder.keyId())) {
        throw new IllegalArgumentException(
            "Attestation genesis founders must have distinct principals and keys.");
      }
    }
  }
}
