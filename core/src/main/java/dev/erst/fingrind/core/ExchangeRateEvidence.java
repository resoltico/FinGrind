package dev.erst.fingrind.core;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Canonical rate observation used for one transaction-currency measurement. */
public record ExchangeRateEvidence(
    CurrencyUnit transactionCurrency,
    CurrencyUnit functionalCurrency,
    ExchangeRate exchangeRate,
    Instant observedAt,
    ExchangeRateSourceKind sourceKind,
    Optional<String> sourceReference) {
  /** Validates one exchange-rate evidence fact. */
  public ExchangeRateEvidence {
    Objects.requireNonNull(transactionCurrency, "transactionCurrency");
    Objects.requireNonNull(functionalCurrency, "functionalCurrency");
    Objects.requireNonNull(exchangeRate, "exchangeRate");
    Objects.requireNonNull(observedAt, "observedAt");
    Objects.requireNonNull(sourceKind, "sourceKind");
    Objects.requireNonNull(sourceReference, "sourceReference");
    sourceReference =
        sourceReference.map(
            value -> {
              String normalized = value.strip();
              if (normalized.isEmpty()) {
                throw new IllegalArgumentException(
                    "Exchange-rate source reference must not be blank when present.");
              }
              if (normalized.length() > 255) {
                throw new IllegalArgumentException(
                    "Exchange-rate source reference must not exceed 255 characters.");
              }
              return normalized;
            });
  }
}
