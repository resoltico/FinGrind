package dev.erst.fingrind.contract;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Per-step execution status recorded in a ledger-plan journal. */
public enum LedgerStepStatus {
  /** The step completed successfully. */
  SUCCEEDED,
  /** The step received a deterministic domain rejection. */
  REJECTED,
  /** The step assertion evaluated false. */
  ASSERTION_FAILED;

  /** Returns the stable wire value for this step status. */
  public String wireValue() {
    return switch (this) {
      case SUCCEEDED -> "succeeded";
      case REJECTED -> "rejected";
      case ASSERTION_FAILED -> "assertion-failed";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return Arrays.stream(values()).map(LedgerStepStatus::wireValue).toList();
  }

  /** Parses one stable wire value. */
  public static LedgerStepStatus fromWireValue(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    return Arrays.stream(values())
        .filter(value -> value.wireValue().equals(wireValue))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unsupported ledger step status: " + wireValue));
  }
}
