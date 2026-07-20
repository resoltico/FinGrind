package dev.erst.fingrind.core.attestation;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Names one signed structure's authorization requirements and permitted provenance. */
final class AttestationAuthorizationScope {
  private final AttestationCapability capability;
  private final @Nullable AttestationSourceChannel sourceChannel;
  private final @Nullable AttestationSystemWorkflowKind requiredSystemWorkflowKind;

  private AttestationAuthorizationScope(
      AttestationCapability capability,
      @Nullable AttestationSourceChannel sourceChannel,
      @Nullable AttestationSystemWorkflowKind requiredSystemWorkflowKind) {
    this.capability = Objects.requireNonNull(capability, "capability");
    this.sourceChannel = sourceChannel;
    this.requiredSystemWorkflowKind = requiredSystemWorkflowKind;
  }

  static AttestationAuthorizationScope operation(
      AttestationOperationKind operationKind, AttestationSourceChannel sourceChannel) {
    AttestationOperationKind checkedOperationKind =
        Objects.requireNonNull(operationKind, "operationKind");
    AttestationSourceChannel checkedSourceChannel =
        Objects.requireNonNull(sourceChannel, "sourceChannel");
    return new AttestationAuthorizationScope(
        AttestationCapability.forOperation(checkedOperationKind),
        checkedSourceChannel,
        checkedSourceChannel == AttestationSourceChannel.SYSTEM
            ? requiredSystemWorkflowKind(checkedOperationKind)
            : null);
  }

  static AttestationAuthorizationScope manifest() {
    return new AttestationAuthorizationScope(AttestationCapability.BACKUP, null, null);
  }

  static AttestationAuthorizationScope receipt() {
    return new AttestationAuthorizationScope(AttestationCapability.ANCHOR, null, null);
  }

  AttestationCapability capability() {
    return capability;
  }

  @Nullable AttestationSourceChannel sourceChannel() {
    return sourceChannel;
  }

  @Nullable AttestationSystemWorkflowKind requiredSystemWorkflowKind() {
    return requiredSystemWorkflowKind;
  }

  private static AttestationSystemWorkflowKind requiredSystemWorkflowKind(
      AttestationOperationKind operationKind) {
    if (operationKind == AttestationOperationKind.INTERIM_RESULT_SWEEP) {
      return AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP;
    }
    if (operationKind == AttestationOperationKind.FISCAL_YEAR_CLOSE) {
      return AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE;
    }
    throw new AttestationAuthorizationException(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }
}
