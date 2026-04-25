package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable transaction semantics for ledger-plan execution. */
public enum PlanTransactionMode implements WireValue {
  ATOMIC("atomic");

  private final String wireValue;

  PlanTransactionMode(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this plan transaction mode. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable plan transaction mode in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(PlanTransactionMode.class);
  }

  /** Parses one stable plan transaction mode. */
  public static PlanTransactionMode fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        PlanTransactionMode.class, wireValue, "Unsupported ledger-plan transaction mode");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
