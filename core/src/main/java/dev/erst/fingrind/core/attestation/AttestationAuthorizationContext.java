package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
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
  private final BigInteger resolvingOrder;
  private final AttestationCapability capability;
  private final @Nullable AttestationSourceChannel sourceChannel;
  private final @Nullable AttestationSystemWorkflowKind requiredSystemWorkflowKind;

  private AttestationAuthorizationContext(
      byte[] payload,
      BigInteger resolvingOrder,
      AttestationCapability capability,
      @Nullable AttestationSourceChannel sourceChannel,
      @Nullable AttestationSystemWorkflowKind requiredSystemWorkflowKind) {
    this.payload = Objects.requireNonNull(payload, "payload").clone();
    this.resolvingOrder =
        AttestationUnsignedEncoding.requireUnsigned(resolvingOrder, Long.BYTES, "resolvingOrder");
    this.capability = Objects.requireNonNull(capability, "capability");
    this.sourceChannel = sourceChannel;
    this.requiredSystemWorkflowKind = requiredSystemWorkflowKind;
  }

  static AttestationAuthorizationContext operation(
      AttestationOperationPayload payload, AttestationSourceChannel sourceChannel) {
    AttestationOperationPayload checkedPayload = Objects.requireNonNull(payload, "payload");
    AttestationSourceChannel checkedSourceChannel =
        Objects.requireNonNull(sourceChannel, "sourceChannel");
    AttestationOperationKind operationKind =
        AttestationOperationKind.forWireToken(checkedPayload.operationKind());
    BigInteger operationOrder = checkedPayload.operationOrder();
    if (operationOrder.signum() == 0) {
      throw failure(AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
    }
    return new AttestationAuthorizationContext(
        checkedPayload.encoded(),
        operationOrder.subtract(BigInteger.ONE),
        operationKind.capability(),
        checkedSourceChannel,
        checkedSourceChannel == AttestationSourceChannel.SYSTEM
            ? requiredSystemWorkflowKind(operationKind)
            : null);
  }

  static AttestationAuthorizationContext manifest(AttestationBackupManifestPayload payload) {
    AttestationBackupManifestPayload checkedPayload = Objects.requireNonNull(payload, "payload");
    return new AttestationAuthorizationContext(
        checkedPayload.encoded(),
        checkedPayload.sourceOrder(),
        AttestationCapability.BACKUP,
        null,
        null);
  }

  static AttestationAuthorizationContext receipt(AttestationReceiptPayload payload) {
    AttestationReceiptPayload checkedPayload = Objects.requireNonNull(payload, "payload");
    return new AttestationAuthorizationContext(
        checkedPayload.encoded(),
        checkedPayload.operationOrder(),
        AttestationCapability.ANCHOR,
        null,
        null);
  }

  boolean matchesPayload(byte[] candidate) {
    return Arrays.equals(payload, candidate);
  }

  byte[] payload() {
    return payload.clone();
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
