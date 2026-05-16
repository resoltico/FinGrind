package dev.erst.fingrind.executor.bookkeeping.policy;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.Objects;

/** Policy-owned descriptor for one derived equity line published into statement outputs. */
public record DerivedEquityLine(
    String lineCode, String lineName, FinancialPositionLineClassification lineClassification) {
  public DerivedEquityLine {
    lineCode = Objects.requireNonNull(lineCode, "lineCode").strip();
    lineName = Objects.requireNonNull(lineName, "lineName").strip();
    Objects.requireNonNull(lineClassification, "lineClassification");
    if (lineCode.isEmpty()) {
      throw new IllegalArgumentException("lineCode must not be blank.");
    }
    if (lineName.isEmpty()) {
      throw new IllegalArgumentException("lineName must not be blank.");
    }
    if (lineClassification.accountType() != AccountType.EQUITY) {
      throw new IllegalArgumentException(
          "Derived equity lines must use one equity financialPositionLineClassification.");
    }
  }
}
