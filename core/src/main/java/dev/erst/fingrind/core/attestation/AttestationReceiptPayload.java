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

  /** Decodes one complete receipt payload at the raw attestation boundary. */
  static AttestationReceiptPayload decode(byte[] encoded) {
    try {
      AttestationByteReader input =
          new AttestationByteReader(encoded, AttestationAuthorizationFailure.RECEIPT_INVALID);
      input.requireAscii("FGATTRC1");
      if (input.readUnsigned(Byte.BYTES).intValueExact() != 1) {
        throw new AttestationAuthorizationException(
            AttestationAuthorizationFailure.UNSUPPORTED_VERSION);
      }
      AttestationReceiptPayload payload =
          new AttestationReceiptPayload(
              input.readUuid(),
              input.readUnsigned(Long.BYTES),
              input.readHash(),
              input.readInstant());
      if (!AttestationAlgorithm.ED25519.id().equals(input.readToken())) {
        throw new AttestationAuthorizationException(
            AttestationAuthorizationFailure.KEY_ALGORITHM_INVALID);
      }
      input.requireAtEnd();
      return payload;
    } catch (AttestationAuthorizationException exception) {
      throw exception;
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw new AttestationAuthorizationException(AttestationAuthorizationFailure.RECEIPT_INVALID);
    }
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
    AttestationTextEncoding.appendToken(output, AttestationAlgorithm.ED25519.id(), "algorithmId");
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
}
