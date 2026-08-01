package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.HexFormat;
import java.util.Objects;

/** One authenticated operation position and head extracted from verified immutable evidence. */
public final class AttestationOperationCommitment {
  private static final int HEAD_BYTE_LENGTH = 32;

  private final BigInteger operationOrder;
  private final byte[] operationHead;

  /** Creates one defensive immutable commitment reference. */
  public AttestationOperationCommitment(BigInteger operationOrder, byte[] operationHead) {
    this.operationOrder = requireOperationOrder(operationOrder);
    this.operationHead = requireOperationHead(operationHead);
  }

  /** Returns the unsigned 64-bit operation order. */
  public BigInteger operationOrder() {
    return operationOrder;
  }

  /** Returns a defensive copy of the authenticated 32-byte operation head. */
  public byte[] operationHead() {
    return operationHead.clone();
  }

  /** Returns the authenticated operation head as 64 lowercase hexadecimal characters. */
  public String operationHeadHex() {
    return HexFormat.of().formatHex(operationHead);
  }

  private static BigInteger requireOperationOrder(BigInteger operationOrder) {
    BigInteger checkedOrder = Objects.requireNonNull(operationOrder, "operationOrder");
    if (checkedOrder.signum() < 0 || checkedOrder.bitLength() > Long.SIZE) {
      throw new IllegalArgumentException("operationOrder must be an unsigned 64-bit value.");
    }
    return checkedOrder;
  }

  private static byte[] requireOperationHead(byte[] operationHead) {
    byte[] checkedHead = Objects.requireNonNull(operationHead, "operationHead").clone();
    if (checkedHead.length != HEAD_BYTE_LENGTH) {
      throw new IllegalArgumentException("operationHead must contain exactly 32 bytes.");
    }
    return checkedHead;
  }
}
