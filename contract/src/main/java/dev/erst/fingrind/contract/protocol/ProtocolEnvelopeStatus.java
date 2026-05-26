package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Canonical top-level JSON-envelope statuses emitted by FinGrind CLI surfaces. */
public enum ProtocolEnvelopeStatus implements WireValue {
  /** The command completed successfully and returned a payload. */
  OK("ok"),
  /** The command was understood but rejected deterministically by domain or workflow rules. */
  REJECTED("rejected"),
  /** The command failed due to invalid input, assertion failure, or runtime error. */
  ERROR("error");

  private final String wireValue;

  ProtocolEnvelopeStatus(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns the canonical wire values in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ProtocolEnvelopeStatus.class);
  }

  /** Parses one canonical envelope status from its wire value. */
  public static ProtocolEnvelopeStatus fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        ProtocolEnvelopeStatus.class, wireValue, "Unsupported protocol envelope status");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
