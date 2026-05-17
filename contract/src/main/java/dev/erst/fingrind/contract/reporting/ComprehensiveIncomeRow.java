package dev.erst.fingrind.contract.reporting;

import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.OtherComprehensiveIncomeClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.Objects;
import java.util.Optional;

/** One profit-or-loss or OCI presentation row. */
public record ComprehensiveIncomeRow(
    String lineCode,
    String lineName,
    Optional<ProfitAndLossLineClassification> profitAndLossLineClassification,
    Optional<OtherComprehensiveIncomeClassification> otherComprehensiveIncomeClassification,
    Money amount) {
  /** Validates one comprehensive-income row. */
  public ComprehensiveIncomeRow {
    lineCode = normalize(lineCode, "lineCode");
    lineName = normalize(lineName, "lineName");
    Objects.requireNonNull(profitAndLossLineClassification, "profitAndLossLineClassification");
    Objects.requireNonNull(
        otherComprehensiveIncomeClassification, "otherComprehensiveIncomeClassification");
    Objects.requireNonNull(amount, "amount");
    if (profitAndLossLineClassification.isEmpty()
        && otherComprehensiveIncomeClassification.isEmpty()) {
      throw new IllegalArgumentException(
          "Comprehensive-income row must declare either profit/loss or OCI classification.");
    }
  }

  private static String normalize(String value, String fieldName) {
    String normalized = Objects.requireNonNull(value, fieldName).strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return normalized;
  }
}
