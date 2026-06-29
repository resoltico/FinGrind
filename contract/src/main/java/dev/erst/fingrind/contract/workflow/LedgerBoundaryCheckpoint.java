package dev.erst.fingrind.contract.workflow;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable journal-visible checkpoints for plan-boundary execution failures. */
public enum LedgerBoundaryCheckpoint implements WireValue {
  BEGIN("begin"),
  INITIALIZATION_CHECK("initialization-check"),
  COMMIT("commit"),
  ROLLBACK("rollback");

  private final String wireValue;

  LedgerBoundaryCheckpoint(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public boundary-checkpoint wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(LedgerBoundaryCheckpoint.class);
  }

  /** Parses one stable public boundary-checkpoint wire value. */
  public static LedgerBoundaryCheckpoint fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        LedgerBoundaryCheckpoint.class, wireValue, "Unsupported ledger boundary checkpoint");
  }
}
