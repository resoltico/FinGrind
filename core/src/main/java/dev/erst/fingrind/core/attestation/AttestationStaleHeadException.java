package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

/** Typed refusal when an attestation authorizes a head that is no longer current at admission. */
public final class AttestationStaleHeadException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  private final byte[] observedHead;
  private final byte[] currentHead;
  private final BigInteger currentOrder;

  /** Creates a refusal that carries the observed head and the current chain position. */
  public AttestationStaleHeadException(
      byte[] observedHead, byte[] currentHead, BigInteger currentOrder) {
    super("stale-head");
    this.observedHead = copy(observedHead, "observedHead");
    this.currentHead = copy(currentHead, "currentHead");
    this.currentOrder = Objects.requireNonNull(currentOrder, "currentOrder");
  }

  /** Returns a defensive copy of the stale head used for authorization. */
  public byte[] observedHead() {
    return observedHead.clone();
  }

  /** Returns a defensive copy of the authenticated current head. */
  public byte[] currentHead() {
    return currentHead.clone();
  }

  /** Returns the order of the authenticated current head. */
  public BigInteger currentOrder() {
    return currentOrder;
  }

  private static byte[] copy(byte[] value, String name) {
    return Arrays.copyOf(Objects.requireNonNull(value, name), value.length);
  }
}
