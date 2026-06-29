package dev.erst.fingrind.contract.fx;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.util.Objects;

/**
 * Owned transaction-currency, translated functional amount, quote, and treatment facts for one
 * posting.
 */
public record ForeignExchangeDetails(
    MonetaryAmount transactionAmount,
    MonetaryAmount functionalAmount,
    QuotedExchangeRate quotedExchangeRate,
    ForeignExchangeTreatmentKind treatmentKind) {
  /** Validates one owned foreign-exchange fact bundle. */
  public ForeignExchangeDetails {
    Objects.requireNonNull(transactionAmount, "transactionAmount");
    Objects.requireNonNull(functionalAmount, "functionalAmount");
    Objects.requireNonNull(quotedExchangeRate, "quotedExchangeRate");
    Objects.requireNonNull(treatmentKind, "treatmentKind");
    requirePositive(transactionAmount, "transactionAmount");
    requirePositive(functionalAmount, "functionalAmount");
    if (transactionAmount.currencyCode().equals(functionalAmount.currencyCode())) {
      throw new IllegalArgumentException(
          "Foreign-exchange details require distinct transaction and functional currencies.");
    }
    if (!transactionAmount
        .currencyCode()
        .equals(quotedExchangeRate.transactionCurrencyAmount().currencyCode())) {
      throw new IllegalArgumentException(
          "transactionAmount currencyCode must match quotedExchangeRate transaction currency.");
    }
    if (!functionalAmount
        .currencyCode()
        .equals(quotedExchangeRate.functionalCurrencyAmount().currencyCode())) {
      throw new IllegalArgumentException(
          "functionalAmount currencyCode must match quotedExchangeRate functional currency.");
    }
    if (!functionalAmount.equals(quotedExchangeRate.translate(transactionAmount))) {
      throw new IllegalArgumentException(
          "functionalAmount must equal the half-up translation of transactionAmount through quotedExchangeRate.");
    }
  }

  private static void requirePositive(MonetaryAmount amount, String fieldName) {
    if (!amount.toMoney().isPositive()) {
      throw new IllegalArgumentException(fieldName + " must carry one positive amount.");
    }
  }
}
