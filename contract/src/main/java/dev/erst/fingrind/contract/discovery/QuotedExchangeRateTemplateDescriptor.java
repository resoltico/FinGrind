package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/**
 * Canonical request-side quoted exchange-rate facts nested inside one foreign-exchange descriptor.
 */
public record QuotedExchangeRateTemplateDescriptor(
    MonetaryAmount transactionCurrencyAmount,
    MonetaryAmount functionalCurrencyAmount,
    String quotedOn,
    String quoteSource)
    implements TemplateDescriptorType {
  /** Validates one quoted exchange-rate template descriptor payload. */
  public QuotedExchangeRateTemplateDescriptor {
    transactionCurrencyAmount =
        ContractDescriptorValidation.requireValue(
            transactionCurrencyAmount, "transactionCurrencyAmount");
    functionalCurrencyAmount =
        ContractDescriptorValidation.requireValue(
            functionalCurrencyAmount, "functionalCurrencyAmount");
    quotedOn = ContractDescriptorValidation.requireText(quotedOn, "quotedOn");
    quoteSource = ContractDescriptorValidation.requireText(quoteSource, "quoteSource");
    if (!transactionCurrencyAmount.toMoney().isPositive()) {
      throw new IllegalArgumentException(
          "transactionCurrencyAmount must carry one positive amount.");
    }
    if (!functionalCurrencyAmount.toMoney().isPositive()) {
      throw new IllegalArgumentException(
          "functionalCurrencyAmount must carry one positive amount.");
    }
  }
}
