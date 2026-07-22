package dev.erst.fingrind.core.attestation;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One public, attested mutation of the credential registry or authorization policy.
 *
 * <p>The mutation is deliberately closed: enrollment, rollover, revocation, and policy changes are
 * the only operations allowed to alter future authorization. Each value projects the exact request
 * and effect preimages that the verifier independently replays.
 */
public sealed interface AttestationRegistryMutation
    permits AttestationRegistryMutation.EnrollKey,
        AttestationRegistryMutation.RolloverKey,
        AttestationRegistryMutation.RevokeKey,
        AttestationRegistryMutation.AlterPolicy {

  /** Returns the closed operation kind that owns this registry mutation. */
  AttestationOperationKind operationKind();

  /** Returns the canonical request and effect preimages to append for this mutation. */
  AttestationOperationPreimages preimages();

  /** Enrolls one new credential with no predecessor. */
  record EnrollKey(
      UUID principalId,
      AttestationPublicCredential credential,
      AttestationCredentialPurpose purpose)
      implements AttestationRegistryMutation {
    public EnrollKey {
      Objects.requireNonNull(principalId, "principalId");
      Objects.requireNonNull(credential, "credential");
      Objects.requireNonNull(purpose, "purpose");
    }

    @Override
    public AttestationOperationKind operationKind() {
      return AttestationOperationKind.ENROLL_KEY;
    }

    @Override
    public AttestationOperationPreimages preimages() {
      return AttestationLifecycleMutationProjection.enrollKey(this);
    }
  }

  /** Adds one replacement credential for an active credential of the same principal. */
  record RolloverKey(
      UUID principalId,
      AttestationPublicCredential credential,
      AttestationCredentialPurpose purpose,
      AttestationPublicCredential predecessorCredential)
      implements AttestationRegistryMutation {
    public RolloverKey {
      Objects.requireNonNull(principalId, "principalId");
      Objects.requireNonNull(credential, "credential");
      Objects.requireNonNull(purpose, "purpose");
      Objects.requireNonNull(predecessorCredential, "predecessorCredential");
      if (java.util.Arrays.equals(credential.keyId(), predecessorCredential.keyId())) {
        throw new IllegalArgumentException(
            "Attestation rollover credential must differ from predecessor.");
      }
    }

    @Override
    public AttestationOperationKind operationKind() {
      return AttestationOperationKind.ROLLOVER_KEY;
    }

    @Override
    public AttestationOperationPreimages preimages() {
      return AttestationLifecycleMutationProjection.rolloverKey(this);
    }
  }

  /** Permanently revokes one prior credential binding. */
  record RevokeKey(
      UUID principalId, AttestationPublicCredential credential, Optional<String> reason)
      implements AttestationRegistryMutation {
    public RevokeKey {
      Objects.requireNonNull(principalId, "principalId");
      Objects.requireNonNull(credential, "credential");
      reason = Objects.requireNonNull(reason, "reason").map(RevokeKey::requireNonBlankReason);
    }

    @Override
    public AttestationOperationKind operationKind() {
      return AttestationOperationKind.REVOKE_KEY;
    }

    @Override
    public AttestationOperationPreimages preimages() {
      return AttestationLifecycleMutationProjection.revokeKey(this);
    }

    private static String requireNonBlankReason(String value) {
      String checked = Objects.requireNonNull(value, "reason");
      if (checked.isBlank()) {
        throw new IllegalArgumentException("Attestation revocation reason must not be blank.");
      }
      return checked;
    }
  }

  /** Changes one or more future quorum, grant, or autonomous-workflow policy facts. */
  record AlterPolicy(
      List<PolicyRule> policyRules,
      List<CapabilityGrant> capabilityGrants,
      List<SystemWorkflowPolicy> systemWorkflowPolicies)
      implements AttestationRegistryMutation {
    public AlterPolicy {
      policyRules = List.copyOf(Objects.requireNonNull(policyRules, "policyRules"));
      capabilityGrants = List.copyOf(Objects.requireNonNull(capabilityGrants, "capabilityGrants"));
      systemWorkflowPolicies =
          List.copyOf(Objects.requireNonNull(systemWorkflowPolicies, "systemWorkflowPolicies"));
      if (policyRules.isEmpty() && capabilityGrants.isEmpty() && systemWorkflowPolicies.isEmpty()) {
        throw new IllegalArgumentException(
            "Attestation policy mutation must contain at least one change.");
      }
      requireDistinctPolicyRules(policyRules);
      requireDistinctCapabilityGrants(capabilityGrants);
      requireDistinctWorkflowPolicies(systemWorkflowPolicies);
    }

    @Override
    public AttestationOperationKind operationKind() {
      return AttestationOperationKind.ALTER_POLICY;
    }

    @Override
    public AttestationOperationPreimages preimages() {
      return AttestationLifecycleMutationProjection.alterPolicy(this);
    }

    private static void requireDistinctPolicyRules(List<PolicyRule> policyRules) {
      Set<AttestationCapability> capabilities = EnumSet.noneOf(AttestationCapability.class);
      for (PolicyRule rule : policyRules) {
        if (!capabilities.add(rule.capability())) {
          throw new IllegalArgumentException(
              "Attestation policy mutation must not repeat one capability rule.");
        }
      }
    }

    private static void requireDistinctCapabilityGrants(List<CapabilityGrant> capabilityGrants) {
      Set<String> grantIdentities = new HashSet<>();
      for (CapabilityGrant grant : capabilityGrants) {
        String identity = grant.principalId() + ":" + grant.capability();
        if (!grantIdentities.add(identity)) {
          throw new IllegalArgumentException(
              "Attestation policy mutation must not repeat one principal capability grant.");
        }
      }
    }

    private static void requireDistinctWorkflowPolicies(
        List<SystemWorkflowPolicy> systemWorkflowPolicies) {
      Set<UUID> workflowIds = new HashSet<>();
      for (SystemWorkflowPolicy policy : systemWorkflowPolicies) {
        if (!workflowIds.add(policy.workflowId())) {
          throw new IllegalArgumentException(
              "Attestation policy mutation must not repeat one system workflow ID.");
        }
      }
    }
  }

  /** Replaces the effective quorum for one capability after the current operation. */
  record PolicyRule(AttestationCapability capability, int quorum) {
    public PolicyRule {
      Objects.requireNonNull(capability, "capability");
      if (quorum < AttestationAuthorizationLimits.MINIMUM_QUORUM
          || quorum > AttestationAuthorizationLimits.MAXIMUM_QUORUM) {
        throw new IllegalArgumentException(
            "Attestation policy quorum must be between "
                + AttestationAuthorizationLimits.MINIMUM_QUORUM
                + " and "
                + AttestationAuthorizationLimits.MAXIMUM_QUORUM
                + ".");
      }
    }
  }

  /** Grants or revokes a principal's eligibility for one capability after the current operation. */
  record CapabilityGrant(
      UUID principalId, AttestationCapability capability, AttestationGrantState state) {
    public CapabilityGrant {
      Objects.requireNonNull(principalId, "principalId");
      Objects.requireNonNull(capability, "capability");
      Objects.requireNonNull(state, "state");
    }
  }

  /** Activates or retires one autonomous system workflow after the current operation. */
  record SystemWorkflowPolicy(
      UUID workflowId,
      AttestationSystemWorkflowKind workflowKind,
      String resultHoldingAccountCode,
      @Nullable String capitalAccountCode,
      @Nullable String retainedResultAccountCode,
      boolean active) {
    public SystemWorkflowPolicy {
      Objects.requireNonNull(workflowId, "workflowId");
      Objects.requireNonNull(workflowKind, "workflowKind");
      requireNonBlank(resultHoldingAccountCode, "resultHoldingAccountCode");
      if (workflowKind.requiresCapitalAndRetainedResultAccounts()) {
        requireNonBlank(capitalAccountCode, "capitalAccountCode");
        requireNonBlank(retainedResultAccountCode, "retainedResultAccountCode");
      } else if (capitalAccountCode != null || retainedResultAccountCode != null) {
        throw new IllegalArgumentException(
            "Attestation interim workflows must omit capital and retained-result accounts.");
      }
    }

    private static void requireNonBlank(@Nullable String value, String name) {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("Attestation %s must be present.".formatted(name));
      }
    }
  }
}
