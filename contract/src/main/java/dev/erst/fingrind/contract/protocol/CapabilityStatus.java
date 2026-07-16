package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Published implementation status for one bounded product capability. */
public enum CapabilityStatus implements WireValue {
  IMPLEMENTED("implemented"),
  PARTIAL("partial"),
  EXCLUDED("excluded");

  private final String wireValue;

  CapabilityStatus(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns all stable capability-status tokens in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(CapabilityStatus.class);
  }

  /** Parses one stable capability-status token. */
  public static CapabilityStatus fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        CapabilityStatus.class, wireValue, "Unsupported capability status");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
