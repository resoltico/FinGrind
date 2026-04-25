package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Canonical runtime-failure status token emitted by FinGrind JSON envelopes. */
public enum ProtocolFailureStatus implements WireValue {
  /** Runtime, invocation, or invalid-request failure status. */
  ERROR("error");

  private final String wireValue;

  ProtocolFailureStatus(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable machine-readable value for this failure status. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable failure-status token in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ProtocolFailureStatus.class);
  }

  /** Parses one stable failure-status token. */
  public static ProtocolFailureStatus fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        ProtocolFailureStatus.class, wireValue, "Unsupported protocol failure status");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
