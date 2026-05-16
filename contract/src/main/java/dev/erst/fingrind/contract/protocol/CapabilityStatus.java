package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Structured lifecycle status for public accounting capabilities and extension seams. */
public enum CapabilityStatus implements WireValue {
  IMPLEMENTED("implemented"),
  PLANNED("planned"),
  FUTURE_CONTEXT("future-context"),
  DELIBERATELY_EXCLUDED("deliberately-excluded"),
  UNSUPPORTED("unsupported");

  private final String wireValue;

  CapabilityStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(CapabilityStatus.class);
  }

  /** Parses one stable public wire value. */
  public static CapabilityStatus fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        CapabilityStatus.class, wireValue, "Unsupported capabilityStatus");
  }
}
