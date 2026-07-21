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
  private final String algorithmId;

  AttestationOperationPayload(
      UUID bookId,
      BigInteger operationOrder,
      String operationKind,
      AttestationHash previousHead,
      Instant recordedAt,
      AttestationHash requestDigest,
      AttestationHash effectDigest) {
    this(
        bookId,
        operationOrder,
        operationKind,
        previousHead,
        recordedAt,
        requestDigest,
        effectDigest,
        AttestationAlgorithm.ED25519.id());
  }

  private AttestationOperationPayload(
      UUID bookId,
      BigInteger operationOrder,
      String operationKind,
      AttestationHash previousHead,
      Instant recordedAt,
      AttestationHash requestDigest,
      AttestationHash effectDigest,
      String algorithmId) {
    this.bookId = Objects.requireNonNull(bookId, "bookId");
    this.operationOrder =
        AttestationUnsignedEncoding.requireUnsigned(operationOrder, Long.BYTES, "operationOrder");
    this.operationKind = Objects.requireNonNull(operationKind, "operationKind");
    this.previousHead = Objects.requireNonNull(previousHead, "previousHead");
    this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    this.requestDigest = Objects.requireNonNull(requestDigest, "requestDigest");
    this.effectDigest = Objects.requireNonNull(effectDigest, "effectDigest");
    this.algorithmId = Objects.requireNonNull(algorithmId, "algorithmId");
  }

  /** Decodes one complete operation payload at the raw attestation boundary. */
  static AttestationOperationPayload decode(byte[] encoded) {
    return AttestationFormatFailure.decoding(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () -> {
          AttestationByteReader input =
              new AttestationByteReader(encoded, AttestationAuthorizationFailure.PREIMAGE_INVALID);
          AttestationOperationPayload payload = decode(input);
          input.requireAtEnd();
          return payload;
        });
  }

  static AttestationOperationPayload decode(AttestationByteReader input) {
    input.requireAscii("FGATTOP1");
    requireVersion(input);
    java.util.UUID bookId = input.readUuid();
    BigInteger operationOrder = input.readUnsigned(Long.BYTES);
    String operationKind = AttestationCanonicalValueReader.token(input);
    String algorithmId = AttestationCanonicalValueReader.algorithmId(input);
    return new AttestationOperationPayload(
        bookId,
        operationOrder,
        operationKind,
        input.readHash(),
        AttestationCanonicalValueReader.instant(input),
        input.readHash(),
        input.readHash(),
        algorithmId);
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
    AttestationTextEncoding.appendAlgorithmId(output, algorithmId);
    AttestationEncoding.appendHash(output, previousHead);
    AttestationTextEncoding.appendInstant(output, recordedAt, "recordedAt");
    AttestationEncoding.appendHash(output, requestDigest);
    AttestationEncoding.appendHash(output, effectDigest);
    return output.toByteArray();
  }

  BigInteger operationOrder() {
    return operationOrder;
  }

  UUID bookId() {
    return bookId;
  }

  String operationKind() {
    return operationKind;
  }

  AttestationHash previousHead() {
    return previousHead;
  }

  Instant recordedAt() {
    return recordedAt;
  }

  AttestationHash requestDigest() {
    return requestDigest;
  }

  AttestationHash effectDigest() {
    return effectDigest;
  }

  @Override
  public String algorithmId() {
    return algorithmId;
  }

  private static void requireVersion(AttestationByteReader input) {
    if (input.readUnsigned(Byte.BYTES).intValueExact() != 1) {
      throw new AttestationAuthorizationException(
          AttestationAuthorizationFailure.UNSUPPORTED_VERSION);
    }
  }
}
