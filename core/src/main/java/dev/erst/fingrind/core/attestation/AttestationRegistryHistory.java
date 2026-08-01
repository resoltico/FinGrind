package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Rebuilds the immutable authority ledger from accepted effects, never from mutable storage rows.
 *
 * <p>Only the four registry-bearing operation families may add their corresponding effect facts.
 * The request/effect projection checks make every authority change self-proving before it can
 * influence a later position-resolved authorization decision.
 */
final class AttestationRegistryHistory {
  private final List<AttestationCredentialBinding> bindings = new ArrayList<>();
  private final List<AttestationCredentialRetirement> retirements = new ArrayList<>();
  private final List<AttestationCapabilityGrant> grants = new ArrayList<>();
  private final List<AttestationPolicyRule> policyRules = new ArrayList<>();
  private final List<AttestationSystemWorkflowPolicy> workflowPolicies = new ArrayList<>();

  private AttestationRegistryHistory(
      List<AttestationFounder> founders,
      AttestationGenesisInitialRegistry.InitialRegistry initialRegistry) {
    List<AttestationFounder> checkedFounders =
        List.copyOf(Objects.requireNonNull(founders, "founders"));
    for (AttestationFounder founder : checkedFounders) {
      bindings.add(founder.binding());
    }
    AttestationGenesisInitialRegistry.InitialRegistry checkedInitialRegistry =
        Objects.requireNonNull(initialRegistry, "initialRegistry");
    grants.addAll(checkedInitialRegistry.grants());
    policyRules.addAll(checkedInitialRegistry.policyRules());
  }

  private AttestationRegistryHistory(AttestationRegistryHistory source) {
    bindings.addAll(source.bindings);
    retirements.addAll(source.retirements);
    grants.addAll(source.grants);
    policyRules.addAll(source.policyRules);
    workflowPolicies.addAll(source.workflowPolicies);
  }

  static AttestationRegistryHistory genesis(List<AttestationFounder> founders) {
    List<AttestationFounder> checkedFounders =
        List.copyOf(Objects.requireNonNull(founders, "founders"));
    return new AttestationRegistryHistory(checkedFounders, defaults(checkedFounders));
  }

  static AttestationRegistryHistory genesis(AttestationGenesisAuthorizationContext genesis) {
    AttestationGenesisAuthorizationContext checkedGenesis =
        Objects.requireNonNull(genesis, "genesis");
    return new AttestationRegistryHistory(
        checkedGenesis.founders(), checkedGenesis.initialRegistry());
  }

  AttestationRegistry registry() {
    try {
      return AttestationRegistry.fromVerifierFacts(
          bindings, retirements, grants, policyRules, workflowPolicies);
    } catch (RuntimeException exception) {
      throw AttestationFormatFailure.classify(
          exception, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
    }
  }

  void requireAcceptedState() {
    AttestationRegistry registry = registry();
    AttestationRegistryValidator.requireAcceptedCredentialAlgorithms(bindings);
    try {
      registry.requireAcceptedCapacity();
    } catch (RuntimeException exception) {
      throw AttestationFormatFailure.classify(
          exception, AttestationAuthorizationFailure.CAPABILITY_INVALID);
    }
  }

  AttestationRegistryHistory preview(
      AttestationOperationKind operationKind,
      BigInteger operationOrder,
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage) {
    AttestationRegistryHistory next = new AttestationRegistryHistory(this);
    next.accept(operationKind, operationOrder, requestPreimage, effectPreimage);
    return next;
  }

  void accept(
      AttestationOperationKind operationKind,
      BigInteger operationOrder,
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage) {
    try {
      AttestationRegistryEffectDecoder.DecodedFacts decoded =
          AttestationRegistryEffectDecoder.decode(
              operationKind, operationOrder, requestPreimage, effectPreimage);
      bindings.addAll(decoded.bindings());
      retirements.addAll(decoded.retirements());
      grants.addAll(decoded.grants());
      policyRules.addAll(decoded.policyRules());
      workflowPolicies.addAll(decoded.workflowPolicies());
    } catch (RuntimeException exception) {
      throw AttestationFormatFailure.classify(
          exception, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
    }
  }

  private static AttestationGenesisInitialRegistry.InitialRegistry defaults(
      List<AttestationFounder> founders) {
    List<AttestationCapabilityGrant> grants = new ArrayList<>();
    List<AttestationPolicyRule> policyRules = new ArrayList<>();
    for (AttestationCapability capability : AttestationCapability.values()) {
      policyRules.add(
          new AttestationPolicyRule(
              BigInteger.ZERO, capability, capability.genesisQuorum(founders.size())));
      for (AttestationFounder founder : founders) {
        grants.add(
            new AttestationCapabilityGrant(
                BigInteger.ZERO, founder.principalId(), capability, AttestationGrantState.GRANT));
      }
    }
    return new AttestationGenesisInitialRegistry.InitialRegistry(policyRules, grants);
  }
}
