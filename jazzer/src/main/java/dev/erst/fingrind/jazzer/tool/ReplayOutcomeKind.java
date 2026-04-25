package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Stable deterministic replay outcome vocabulary recorded in committed seed metadata. */
public enum ReplayOutcomeKind implements WireValue {
  SUCCESS("success"),
  EXPECTED_INVALID("expected-invalid"),
  UNEXPECTED_FAILURE("unexpected-failure");

  private final String wireValue;

  ReplayOutcomeKind(String wireValue) {
    this.wireValue = wireValue;
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable outcome wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ReplayOutcomeKind.class);
  }

  /** Parses one stable replay outcome wire value. */
  public static ReplayOutcomeKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        ReplayOutcomeKind.class, wireValue, "Unsupported replay outcome kind");
  }
}
