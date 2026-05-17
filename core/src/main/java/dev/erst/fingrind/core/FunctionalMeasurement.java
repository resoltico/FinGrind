package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical measurement from transaction currency into one book's functional currency. */
public record FunctionalMeasurement(
    Money transactionAmount, Money functionalAmount, ExchangeRateEvidence exchangeRateEvidence) {
  /** Validates one functional-currency measurement. */
  public FunctionalMeasurement {
    Objects.requireNonNull(transactionAmount, "transactionAmount");
    Objects.requireNonNull(functionalAmount, "functionalAmount");
    Objects.requireNonNull(exchangeRateEvidence, "exchangeRateEvidence");
    if (!transactionAmount.currencyUnit().equals(exchangeRateEvidence.transactionCurrency())) {
      throw new IllegalArgumentException(
          "Transaction amount currency must match exchange-rate transaction currency.");
    }
    if (!functionalAmount.currencyUnit().equals(exchangeRateEvidence.functionalCurrency())) {
      throw new IllegalArgumentException(
          "Functional amount currency must match exchange-rate functional currency.");
    }
  }
}
