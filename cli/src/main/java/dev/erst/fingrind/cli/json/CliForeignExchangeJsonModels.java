package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.util.Objects;

/** Foreign-exchange JSON records emitted by the CLI transport layer. */
public interface CliForeignExchangeJsonModels {

  record ForeignExchangePayload(
      MonetaryAmount transactionAmount,
      MonetaryAmount functionalAmount,
      QuotedExchangeRatePayload quotedRate,
      String treatmentKind) {
    public ForeignExchangePayload {
      Objects.requireNonNull(transactionAmount, "transactionAmount");
      Objects.requireNonNull(functionalAmount, "functionalAmount");
      Objects.requireNonNull(quotedRate, "quotedRate");
      treatmentKind = requireText(treatmentKind, "treatmentKind");
    }
  }

  record QuotedExchangeRatePayload(
      MonetaryAmount transactionCurrencyAmount,
      MonetaryAmount functionalCurrencyAmount,
      String quotedOn,
      String quoteSource) {
    public QuotedExchangeRatePayload {
      Objects.requireNonNull(transactionCurrencyAmount, "transactionCurrencyAmount");
      Objects.requireNonNull(functionalCurrencyAmount, "functionalCurrencyAmount");
      quotedOn = requireText(quotedOn, "quotedOn");
      quoteSource = requireText(quoteSource, "quoteSource");
    }
  }
}
