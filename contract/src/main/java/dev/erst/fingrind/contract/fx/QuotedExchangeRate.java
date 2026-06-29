package dev.erst.fingrind.contract.fx;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Exact quoted exchange rate stated as one transaction-currency amount against one functional
 * amount.
 */
public record QuotedExchangeRate(
    MonetaryAmount transactionCurrencyAmount,
    MonetaryAmount functionalCurrencyAmount,
    LocalDate quotedOn,
    String quoteSource) {
  /** Validates one owned quoted exchange rate. */
  public QuotedExchangeRate {
    Objects.requireNonNull(transactionCurrencyAmount, "transactionCurrencyAmount");
    Objects.requireNonNull(functionalCurrencyAmount, "functionalCurrencyAmount");
    Objects.requireNonNull(quotedOn, "quotedOn");
    quoteSource = requireText(quoteSource, "quoteSource");
    requirePositive(transactionCurrencyAmount, "transactionCurrencyAmount");
    requirePositive(functionalCurrencyAmount, "functionalCurrencyAmount");
    if (transactionCurrencyAmount.currencyCode().equals(functionalCurrencyAmount.currencyCode())) {
      throw new IllegalArgumentException(
          "Quoted exchange rate must relate distinct transaction and functional currencies.");
    }
  }

  /**
   * Converts one transaction-currency amount into the quoted functional currency using half-up
   * rounding.
   */
  public MonetaryAmount translate(MonetaryAmount transactionAmount) {
    Objects.requireNonNull(transactionAmount, "transactionAmount");
    if (!transactionAmount.currencyCode().equals(transactionCurrencyAmount.currencyCode())) {
      throw new IllegalArgumentException(
          "transactionAmount currencyCode must match quoted transaction currency.");
    }
    requirePositive(transactionAmount, "transactionAmount");
    BigInteger transactionMinor = new BigInteger(transactionAmount.minorUnits());
    BigInteger quotedTransactionMinor = new BigInteger(transactionCurrencyAmount.minorUnits());
    BigInteger quotedFunctionalMinor = new BigInteger(functionalCurrencyAmount.minorUnits());
    BigInteger numerator = transactionMinor.multiply(quotedFunctionalMinor);
    BigInteger[] division = numerator.divideAndRemainder(quotedTransactionMinor);
    BigInteger quotient = division[0];
    BigInteger remainder = division[1];
    if (remainder.shiftLeft(1).compareTo(quotedTransactionMinor) >= 0) {
      quotient = quotient.add(BigInteger.ONE);
    }
    if (quotient.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
      throw new IllegalArgumentException(
          "Translated functional amount is outside the supported money range.");
    }
    return new MonetaryAmount(
        functionalCurrencyAmount.currencyCode(), Long.toString(quotient.longValueExact()));
  }

  private static void requirePositive(MonetaryAmount amount, String fieldName) {
    if (!amount.toMoney().isPositive()) {
      throw new IllegalArgumentException(fieldName + " must carry one positive amount.");
    }
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return value;
  }
}
