package dev.erst.fingrind.contract;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Final status for one ledger-plan execution. */
public enum LedgerPlanStatus {
  /** Every step completed successfully and the atomic transaction was committed. */
  SUCCEEDED,
  /** A deterministic command rejection stopped the plan and rolled back the transaction. */
  REJECTED,
  /** A ledger assertion failed and rolled back the transaction. */
  ASSERTION_FAILED;

  /** Returns the stable wire value for this plan status. */
  public String wireValue() {
    return switch (this) {
      case SUCCEEDED -> "succeeded";
      case REJECTED -> "rejected";
      case ASSERTION_FAILED -> "assertion-failed";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return Arrays.stream(values()).map(LedgerPlanStatus::wireValue).toList();
  }

  /** Parses one stable wire value. */
  public static LedgerPlanStatus fromWireValue(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    return Arrays.stream(values())
        .filter(value -> value.wireValue().equals(wireValue))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unsupported ledger plan status: " + wireValue));
  }
}
