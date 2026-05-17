package dev.erst.fingrind.contract.reporting;

import dev.erst.fingrind.core.CashFlowActivity;
import dev.erst.fingrind.core.Money;
import java.util.Objects;

/** One presentation line in a statement of cash flows. */
public record CashFlowLine(
    CashFlowActivity cashFlowActivity, String lineCode, String lineName, Money amount) {
  /** Validates one cash-flow line. */
  public CashFlowLine {
    Objects.requireNonNull(cashFlowActivity, "cashFlowActivity");
    lineCode = normalize(lineCode, "lineCode");
    lineName = normalize(lineName, "lineName");
    Objects.requireNonNull(amount, "amount");
  }

  private static String normalize(String value, String fieldName) {
    String normalized = Objects.requireNonNull(value, fieldName).strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return normalized;
  }
}
