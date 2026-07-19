package dev.erst.fingrind.core.attestation;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Canonical non-mutating receipt payload that anchors one operation head. */
final class AttestationReceiptPayload implements AttestationPayload {
  private final UUID bookId;
  private final BigInteger operationOrder;
  private final AttestationHash operationHead;
  private final Instant receiptTimestamp;

  AttestationReceiptPayload(
      UUID bookId,
      BigInteger operationOrder,
      AttestationHash operationHead,
      Instant receiptTimestamp) {
    this.bookId = Objects.requireNonNull(bookId, "bookId");
    this.operationOrder =
        AttestationUnsignedEncoding.requireUnsigned(operationOrder, Long.BYTES, "operationOrder");
    this.operationHead = Objects.requireNonNull(operationHead, "operationHead");
    this.receiptTimestamp = Objects.requireNonNull(receiptTimestamp, "receiptTimestamp");
  }

  @Override
  public byte[] encoded() {
    ByteArrayOutputStream output = new ByteArrayOutputStream(97);
    AttestationTextEncoding.appendAscii(output, "FGATTRC1");
    AttestationUnsignedEncoding.appendByte(output, 1, "receiptVersion");
    AttestationEncoding.appendUuid(output, bookId);
    AttestationUnsignedEncoding.appendUnsigned(
        output, operationOrder, Long.BYTES, "operationOrder");
    AttestationEncoding.appendHash(output, operationHead);
    AttestationTextEncoding.appendInstant(output, receiptTimestamp, "receiptTimestamp");
    AttestationTextEncoding.appendToken(output, AttestationEncoding.ALGORITHM_ID, "algorithmId");
    return output.toByteArray();
  }
}
