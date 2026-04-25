package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable failure semantics for ledger-plan execution. */
public enum PlanFailurePolicy implements WireValue {
  HALT_ON_FIRST_FAILURE("halt-on-first-failure");

  private final String wireValue;

  PlanFailurePolicy(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this plan failure policy. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable plan failure policy in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(PlanFailurePolicy.class);
  }

  /** Parses one stable plan failure policy. */
  public static PlanFailurePolicy fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        PlanFailurePolicy.class, wireValue, "Unsupported ledger-plan failure policy");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
