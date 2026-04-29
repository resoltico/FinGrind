package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable journal-visible phases for plan-boundary execution failures. */
public enum LedgerBoundaryPhase implements WireValue {
  BEGIN("begin"),
  INITIALIZATION_CHECK("initialization-check"),
  COMMIT("commit"),
  ROLLBACK("rollback");

  private final String wireValue;

  LedgerBoundaryPhase(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public boundary-phase wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(LedgerBoundaryPhase.class);
  }

  /** Parses one stable public boundary-phase wire value. */
  public static LedgerBoundaryPhase fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        LedgerBoundaryPhase.class, wireValue, "Unsupported ledger boundary phase");
  }
}
