package dev.erst.fingrind.sqlite;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

/** Typed refusal when a signature was made against a head that is no longer current. */
public final class SqliteAttestationStaleHeadException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  private final byte[] observedHead;
  private final byte[] currentHead;
  private final BigInteger currentOrder;

  /** Creates a refusal that carries the observed head and the current chain position. */
  public SqliteAttestationStaleHeadException(
      byte[] observedHead, byte[] currentHead, BigInteger currentOrder) {
    super("stale-head");
    this.observedHead = copy(observedHead, "observedHead");
    this.currentHead = copy(currentHead, "currentHead");
    this.currentOrder = Objects.requireNonNull(currentOrder, "currentOrder");
  }

  public byte[] observedHead() {
    return observedHead.clone();
  }

  public byte[] currentHead() {
    return currentHead.clone();
  }

  public BigInteger currentOrder() {
    return currentOrder;
  }

  private static byte[] copy(byte[] value, String name) {
    return Arrays.copyOf(Objects.requireNonNull(value, name), value.length);
  }
}
