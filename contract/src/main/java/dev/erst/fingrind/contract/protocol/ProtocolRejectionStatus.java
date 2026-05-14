package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Canonical deterministic rejection-status tokens emitted by FinGrind JSON envelopes. */
public enum ProtocolRejectionStatus implements WireValue {
  /** Deterministic domain rejection status. */
  REJECTED("rejected");

  private final String wireValue;

  ProtocolRejectionStatus(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable machine-readable value for this rejection status. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable rejection-status token in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ProtocolRejectionStatus.class);
  }

  /** Parses one stable rejection-status token. */
  public static ProtocolRejectionStatus fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        ProtocolRejectionStatus.class, wireValue, "Unsupported protocol rejection status");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
