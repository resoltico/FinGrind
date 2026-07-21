package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Authorization facts derived from one decoded signed payload and its verified request provenance.
 *
 * <p>The context is deliberately constructed from the payload that the envelope must carry. This
 * prevents a caller from applying a genuine envelope to a different historical position or
 * capability.
 */
final class AttestationAuthorizationContext {
  private final byte[] payload;
  private final String algorithmId;
  private final BigInteger resolvingOrder;
  private final AttestationCapability capability;
  private final @Nullable AttestationSourceChannel sourceChannel;
  private final @Nullable UUID systemWorkflowId;
  private final @Nullable AttestationSystemWorkflowKind requiredSystemWorkflowKind;

  private AttestationAuthorizationContext(
      byte[] payload,
      String algorithmId,
      BigInteger resolvingOrder,
      AttestationCapability capability,
      @Nullable AttestationSourceChannel sourceChannel,
      @Nullable UUID systemWorkflowId,
      @Nullable AttestationSystemWorkflowKind requiredSystemWorkflowKind) {
    this.payload = Objects.requireNonNull(payload, "payload").clone();
    this.algorithmId = Objects.requireNonNull(algorithmId, "algorithmId");
    this.resolvingOrder =
        AttestationUnsignedEncoding.requireUnsigned(resolvingOrder, Long.BYTES, "resolvingOrder");
    this.capability = Objects.requireNonNull(capability, "capability");
    this.sourceChannel = sourceChannel;
    this.systemWorkflowId = systemWorkflowId;
    this.requiredSystemWorkflowKind = requiredSystemWorkflowKind;
  }

  static AttestationAuthorizationContext operation(
      AttestationOperationPayload payload, AttestationVerifiedOperationProvenance provenance) {
    AttestationOperationPayload checkedPayload = Objects.requireNonNull(payload, "payload");
    AttestationVerifiedOperationProvenance checkedProvenance =
        Objects.requireNonNull(provenance, "provenance");
    if (!checkedProvenance.matches(checkedPayload)) {
      throw failure(AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
    }
    return operation(
        checkedPayload, checkedProvenance.sourceChannel(), checkedProvenance.systemWorkflowId());
  }

  /** Builds the provenance-neutral context used only by standalone shared-envelope fixtures. */
  static AttestationAuthorizationContext standaloneOperation(AttestationOperationPayload payload) {
    return operation(Objects.requireNonNull(payload, "payload"), null, null);
  }

  private static AttestationAuthorizationContext operation(
      AttestationOperationPayload payload,
      @Nullable AttestationSourceChannel sourceChannel,
      @Nullable UUID systemWorkflowId) {
    AttestationOperationKind operationKind =
        AttestationOperationKind.forWireToken(payload.operationKind());
    BigInteger operationOrder = payload.operationOrder();
    if (operationOrder.signum() == 0 || operationKind.isGenesis()) {
      throw failure(AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
    }
    return new AttestationAuthorizationContext(
        payload.encoded(),
        payload.algorithmId(),
        operationOrder.subtract(BigInteger.ONE),
        operationKind.capability(),
        sourceChannel,
        systemWorkflowId,
        sourceChannel == AttestationSourceChannel.SYSTEM
            ? requiredSystemWorkflowKind(operationKind)
            : null);
  }

  static AttestationAuthorizationContext manifest(AttestationBackupManifestPayload payload) {
    AttestationBackupManifestPayload checkedPayload = Objects.requireNonNull(payload, "payload");
    return new AttestationAuthorizationContext(
        checkedPayload.encoded(),
        checkedPayload.algorithmId(),
        checkedPayload.sourceOrder(),
        AttestationCapability.BACKUP,
        null,
        null,
        null);
  }

  static AttestationAuthorizationContext receipt(AttestationReceiptPayload payload) {
    AttestationReceiptPayload checkedPayload = Objects.requireNonNull(payload, "payload");
    return new AttestationAuthorizationContext(
        checkedPayload.encoded(),
        checkedPayload.algorithmId(),
        checkedPayload.operationOrder(),
        AttestationCapability.ANCHOR,
        null,
        null,
        null);
  }

  boolean matchesPayload(byte[] candidate) {
    return Arrays.equals(payload, candidate);
  }

  byte[] payload() {
    return payload.clone();
  }

  String algorithmId() {
    return algorithmId;
  }

  BigInteger resolvingOrder() {
    return resolvingOrder;
  }

  AttestationCapability capability() {
    return capability;
  }

  @Nullable AttestationSourceChannel sourceChannel() {
    return sourceChannel;
  }

  @Nullable UUID systemWorkflowId() {
    return systemWorkflowId;
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
    throw failure(AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }

  private static AttestationAuthorizationException failure(
      AttestationAuthorizationFailure failure) {
    return new AttestationAuthorizationException(failure);
  }
}
