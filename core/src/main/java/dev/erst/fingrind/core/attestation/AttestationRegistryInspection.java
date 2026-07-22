package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Verified effective authority state reconstructed only from immutable attestation evidence. */
public record AttestationRegistryInspection(
    UUID bookId,
    BigInteger headOrder,
    String operationHeadHex,
    List<Credential> credentials,
    List<CapabilityPolicy> capabilityPolicies,
    List<PrincipalCapability> principalCapabilities,
    List<SystemWorkflowPolicy> systemWorkflowPolicies) {
  public AttestationRegistryInspection {
    Objects.requireNonNull(bookId, "bookId");
    Objects.requireNonNull(headOrder, "headOrder");
    if (headOrder.signum() < 0 || headOrder.bitLength() > Long.SIZE) {
      throw new IllegalArgumentException("headOrder must be an unsigned 64-bit value.");
    }
    operationHeadHex = requireOperationHeadHex(operationHeadHex);
    credentials = List.copyOf(Objects.requireNonNull(credentials, "credentials"));
    capabilityPolicies =
        List.copyOf(Objects.requireNonNull(capabilityPolicies, "capabilityPolicies"));
    principalCapabilities =
        List.copyOf(Objects.requireNonNull(principalCapabilities, "principalCapabilities"));
    systemWorkflowPolicies =
        List.copyOf(Objects.requireNonNull(systemWorkflowPolicies, "systemWorkflowPolicies"));
  }

  /** One enrolled public credential and its state at the verified head. */
  public record Credential(
      UUID principalId,
      String keyId,
      String credentialSpki,
      String credentialPurpose,
      String bindingAction,
      BigInteger acceptedOrder,
      @Nullable String predecessorKeyId,
      String state) {
    public Credential {
      Objects.requireNonNull(principalId, "principalId");
      keyId = requireText(keyId, "keyId");
      credentialSpki = requireText(credentialSpki, "credentialSpki");
      credentialPurpose = requireText(credentialPurpose, "credentialPurpose");
      bindingAction = requireText(bindingAction, "bindingAction");
      Objects.requireNonNull(acceptedOrder, "acceptedOrder");
      predecessorKeyId = optionalText(predecessorKeyId, "predecessorKeyId");
      state = requireText(state, "state");
    }
  }

  /** One effective quorum and the eligible principal counts that make it satisfiable. */
  public record CapabilityPolicy(
      String capability,
      int quorum,
      int eligiblePrincipalCount,
      int eligibleOperatorPrincipalCount,
      int eligibleSystemPrincipalCount) {
    public CapabilityPolicy {
      capability = requireText(capability, "capability");
      if (quorum < 1
          || eligiblePrincipalCount < 0
          || eligibleOperatorPrincipalCount < 0
          || eligibleSystemPrincipalCount < 0) {
        throw new IllegalArgumentException("Capability-policy counts must be non-negative.");
      }
    }
  }

  /** One principal's effective authorization decision for one capability at the verified head. */
  public record PrincipalCapability(UUID principalId, String capability, boolean eligible) {
    public PrincipalCapability {
      Objects.requireNonNull(principalId, "principalId");
      capability = requireText(capability, "capability");
    }
  }

  /** One effective autonomous-workflow policy, including retired policies for auditability. */
  public record SystemWorkflowPolicy(
      UUID workflowId,
      String workflowKind,
      String resultHoldingAccountCode,
      @Nullable String capitalAccountCode,
      @Nullable String retainedResultAccountCode,
      boolean active,
      BigInteger acceptedOrder) {
    public SystemWorkflowPolicy {
      Objects.requireNonNull(workflowId, "workflowId");
      workflowKind = requireText(workflowKind, "workflowKind");
      resultHoldingAccountCode = requireText(resultHoldingAccountCode, "resultHoldingAccountCode");
      capitalAccountCode = optionalText(capitalAccountCode, "capitalAccountCode");
      retainedResultAccountCode =
          optionalText(retainedResultAccountCode, "retainedResultAccountCode");
      Objects.requireNonNull(acceptedOrder, "acceptedOrder");
    }
  }

  private static String requireText(String value, String name) {
    String checked = Objects.requireNonNull(value, name);
    if (checked.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank.");
    }
    return checked;
  }

  private static String requireOperationHeadHex(String value) {
    String checked = requireText(value, "operationHeadHex");
    if (!checked.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(
          "operationHeadHex must contain 64 lowercase hexadecimal characters.");
    }
    return checked;
  }

  private static @Nullable String optionalText(@Nullable String value, String name) {
    if (value == null) {
      return null;
    }
    return requireText(value, name);
  }
}
