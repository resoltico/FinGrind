package dev.erst.fingrind.contract.workflow;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Final status for one ledger-plan execution. */
public enum LedgerPlanStatus implements WireValue {
  /** Every step completed successfully and the atomic transaction was committed. */
  SUCCEEDED,
  /** A deterministic command rejection stopped the plan and rolled back the transaction. */
  REJECTED,
  /** A ledger assertion failed and rolled back the transaction. */
  ASSERTION_FAILED;

  /** Returns the stable wire value for this plan status. */
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
    return WireValue.wireValues(LedgerPlanStatus.class);
  }

  /** Parses one stable wire value. */
  public static LedgerPlanStatus fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        LedgerPlanStatus.class, wireValue, "Unsupported ledger plan status");
  }
}
