package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * One already-projected, not-yet-signed operation at its observed chain position.
 *
 * <p>The SQLite mutation boundary creates this value only after it owns the immediate write
 * transaction and has read the current attestation head. The signing seam neither derives business
 * state nor reserves an order: it signs this exact immutable projection or refuses it.
 */
public final class AttestationOperationRequest {
  private static final int HEAD_LENGTH = 32;

  private final UUID bookId;
  private final BigInteger operationOrder;
  private final String operationKind;
  private final byte[] previousHead;
  private final Instant recordedAt;
  private final byte[] requestPreimage;
  private final byte[] effectPreimage;

  /** Owns the complete canonical projection required to form one operation envelope. */
  public AttestationOperationRequest(
      UUID bookId,
      BigInteger operationOrder,
      String operationKind,
      byte[] previousHead,
      Instant recordedAt,
      byte[] requestPreimage,
      byte[] effectPreimage) {
    this.bookId = Objects.requireNonNull(bookId, "bookId");
    this.operationOrder = Objects.requireNonNull(operationOrder, "operationOrder");
    if (operationOrder.signum() < 0 || operationOrder.bitLength() > Long.SIZE) {
      throw new IllegalArgumentException("operationOrder must be an unsigned 64-bit integer.");
    }
    this.operationKind = Objects.requireNonNull(operationKind, "operationKind");
    this.previousHead = copyHead(previousHead, "previousHead");
    this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    this.requestPreimage = copy(requestPreimage, "requestPreimage");
    this.effectPreimage = copy(effectPreimage, "effectPreimage");
  }

  public UUID bookId() {
    return bookId;
  }

  public BigInteger operationOrder() {
    return operationOrder;
  }

  public String operationKind() {
    return operationKind;
  }

  public byte[] previousHead() {
    return previousHead.clone();
  }

  public Instant recordedAt() {
    return recordedAt;
  }

  public byte[] requestPreimage() {
    return requestPreimage.clone();
  }

  public byte[] effectPreimage() {
    return effectPreimage.clone();
  }

  private static byte[] copyHead(byte[] value, String name) {
    byte[] copy = copy(value, name);
    if (copy.length != HEAD_LENGTH) {
      throw new IllegalArgumentException(name + " must contain exactly 32 bytes.");
    }
    return copy;
  }

  private static byte[] copy(byte[] value, String name) {
    return Arrays.copyOf(Objects.requireNonNull(value, name), value.length);
  }
}
