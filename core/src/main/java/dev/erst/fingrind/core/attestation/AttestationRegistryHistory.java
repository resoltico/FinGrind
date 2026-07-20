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
 * influence a later historical authorization decision.
 */
final class AttestationRegistryHistory {
  private final List<AttestationCredentialBinding> bindings = new ArrayList<>();
  private final List<AttestationCredentialRevocation> revocations = new ArrayList<>();
  private final List<AttestationCapabilityGrant> grants = new ArrayList<>();
  private final List<AttestationPolicyRule> policyRules = new ArrayList<>();
  private final List<AttestationSystemWorkflowPolicy> workflowPolicies = new ArrayList<>();

  private AttestationRegistryHistory(List<AttestationFounder> founders) {
    List<AttestationFounder> checkedFounders =
        List.copyOf(Objects.requireNonNull(founders, "founders"));
    for (AttestationFounder founder : checkedFounders) {
      bindings.add(founder.binding());
      for (AttestationCapability capability : AttestationCapability.values()) {
        grants.add(
            new AttestationCapabilityGrant(
                BigInteger.ZERO, founder.principalId(), capability, AttestationGrantState.GRANT));
      }
    }
    for (AttestationCapability capability : AttestationCapability.values()) {
      policyRules.add(
          new AttestationPolicyRule(
              BigInteger.ZERO, capability, capability.genesisQuorum(checkedFounders.size())));
    }
  }

  private AttestationRegistryHistory(AttestationRegistryHistory source) {
    bindings.addAll(source.bindings);
    revocations.addAll(source.revocations);
    grants.addAll(source.grants);
    policyRules.addAll(source.policyRules);
    workflowPolicies.addAll(source.workflowPolicies);
  }

  static AttestationRegistryHistory genesis(List<AttestationFounder> founders) {
    return new AttestationRegistryHistory(founders);
  }

  AttestationRegistry registry() {
    try {
      return AttestationRegistry.fromVerifierFacts(
          bindings, revocations, grants, policyRules, workflowPolicies);
    } catch (RuntimeException exception) {
      throw AttestationFormatFailure.classify(
          exception, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
    }
  }

  void requireAcceptedState() {
    try {
      AttestationRegistry.fromAcceptedHistory(
          bindings, revocations, grants, policyRules, workflowPolicies);
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
    AttestationRegistryEffectDecoder.DecodedFacts decoded =
        AttestationRegistryEffectDecoder.decode(
            operationKind, operationOrder, requestPreimage, effectPreimage);
    bindings.addAll(decoded.bindings());
    revocations.addAll(decoded.revocations());
    grants.addAll(decoded.grants());
    policyRules.addAll(decoded.policyRules());
    workflowPolicies.addAll(decoded.workflowPolicies());
  }
}
