package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Selects how much of one ledger-plan execution journal the CLI returns. */
public enum PlanResultDetail implements WireValue {
  SUMMARY("summary"),
  FULL("full");

  private final String wireValue;

  PlanResultDetail(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns all stable plan-result detail tokens in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(PlanResultDetail.class);
  }

  /** Parses one stable plan-result detail token. */
  public static PlanResultDetail fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        PlanResultDetail.class, wireValue, "Unsupported plan result detail");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
