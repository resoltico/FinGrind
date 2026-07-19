package dev.erst.fingrind.core.attestation;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Canonical operation payload whose envelope hash becomes the operation head. */
final class AttestationOperationPayload implements AttestationPayload {
  private final UUID bookId;
  private final BigInteger operationOrder;
  private final String operationKind;
  private final AttestationHash previousHead;
  private final Instant recordedAt;
  private final AttestationHash requestDigest;
  private final AttestationHash effectDigest;

  AttestationOperationPayload(
      UUID bookId,
      BigInteger operationOrder,
      String operationKind,
      AttestationHash previousHead,
      Instant recordedAt,
      AttestationHash requestDigest,
      AttestationHash effectDigest) {
    this.bookId = Objects.requireNonNull(bookId, "bookId");
    this.operationOrder =
        AttestationUnsignedEncoding.requireUnsigned(operationOrder, Long.BYTES, "operationOrder");
    this.operationKind = Objects.requireNonNull(operationKind, "operationKind");
    this.previousHead = Objects.requireNonNull(previousHead, "previousHead");
    this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    this.requestDigest = Objects.requireNonNull(requestDigest, "requestDigest");
    this.effectDigest = Objects.requireNonNull(effectDigest, "effectDigest");
  }

  @Override
  public byte[] encoded() {
    ByteArrayOutputStream output = new ByteArrayOutputStream(181);
    AttestationTextEncoding.appendAscii(output, "FGATTOP1");
    AttestationUnsignedEncoding.appendByte(output, 1, "payloadVersion");
    AttestationEncoding.appendUuid(output, bookId);
    AttestationUnsignedEncoding.appendUnsigned(
        output, operationOrder, Long.BYTES, "operationOrder");
    AttestationTextEncoding.appendToken(output, operationKind, "operationKind");
    AttestationTextEncoding.appendToken(output, AttestationEncoding.ALGORITHM_ID, "algorithmId");
    AttestationEncoding.appendHash(output, previousHead);
    AttestationTextEncoding.appendInstant(output, recordedAt, "recordedAt");
    AttestationEncoding.appendHash(output, requestDigest);
    AttestationEncoding.appendHash(output, effectDigest);
    return output.toByteArray();
  }
}
