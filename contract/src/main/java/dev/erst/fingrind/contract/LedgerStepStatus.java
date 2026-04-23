package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Per-step execution status recorded in a ledger-plan journal. */
public enum LedgerStepStatus implements WireValue {
  /** The step completed successfully. */
  SUCCEEDED,
  /** The step received a deterministic domain rejection. */
  REJECTED,
  /** The step assertion evaluated false. */
  ASSERTION_FAILED;

  /** Returns the stable wire value for this step status. */
  @Override
  public String wireValue() {
    return switch (this) {
      case SUCCEEDED -> "succeeded";
      case REJECTED -> "rejected";
      case ASSERTION_FAILED -> "assertion-failed";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(LedgerStepStatus.class);
  }

  /** Parses one stable wire value. */
  public static LedgerStepStatus fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        LedgerStepStatus.class, wireValue, "Unsupported ledger step status");
  }
}
