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
  private final String algorithmId;

  AttestationReceiptPayload(
      UUID bookId,
      BigInteger operationOrder,
      AttestationHash operationHead,
      Instant receiptTimestamp) {
    this(
        bookId, operationOrder, operationHead, receiptTimestamp, AttestationAlgorithm.ED25519.id());
  }

  private AttestationReceiptPayload(
      UUID bookId,
      BigInteger operationOrder,
      AttestationHash operationHead,
      Instant receiptTimestamp,
      String algorithmId) {
    this.bookId = Objects.requireNonNull(bookId, "bookId");
    this.operationOrder =
        AttestationUnsignedEncoding.requireUnsigned(operationOrder, Long.BYTES, "operationOrder");
    this.operationHead = Objects.requireNonNull(operationHead, "operationHead");
    this.receiptTimestamp = Objects.requireNonNull(receiptTimestamp, "receiptTimestamp");
    this.algorithmId = Objects.requireNonNull(algorithmId, "algorithmId");
  }

  /** Decodes one complete receipt payload at the raw attestation boundary. */
  static AttestationReceiptPayload decode(byte[] encoded) {
    return AttestationFormatFailure.decoding(
        AttestationAuthorizationFailure.RECEIPT_INVALID,
        () -> {
          AttestationByteReader input =
              new AttestationByteReader(encoded, AttestationAuthorizationFailure.RECEIPT_INVALID);
          AttestationReceiptPayload payload = decode(input);
          input.requireAtEnd();
          return payload;
        });
  }

  static AttestationReceiptPayload decode(AttestationByteReader input) {
    input.requireAscii("FGATTRC1");
    if (input.readUnsigned(Byte.BYTES).intValueExact() != 1) {
      throw new AttestationAuthorizationException(
          AttestationAuthorizationFailure.UNSUPPORTED_VERSION);
    }
    UUID bookId = input.readUuid();
    BigInteger operationOrder = input.readUnsigned(Long.BYTES);
    AttestationHash operationHead = input.readHash();
    Instant receiptTimestamp = AttestationCanonicalValueReader.instant(input);
    String algorithmId = AttestationCanonicalValueReader.algorithmId(input);
    return new AttestationReceiptPayload(
        bookId, operationOrder, operationHead, receiptTimestamp, algorithmId);
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
    AttestationTextEncoding.appendAlgorithmId(output, algorithmId);
    return output.toByteArray();
  }

  BigInteger operationOrder() {
    return operationOrder;
  }

  UUID bookId() {
    return bookId;
  }

  AttestationHash operationHead() {
    return operationHead;
  }

  Instant receiptTimestamp() {
    return receiptTimestamp;
  }

  @Override
  public String algorithmId() {
    return algorithmId;
  }
}
