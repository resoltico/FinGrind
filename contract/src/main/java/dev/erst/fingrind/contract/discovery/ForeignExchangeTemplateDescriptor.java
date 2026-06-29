package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Canonical request-side foreign-exchange facts nested inside one posting request template. */
public record ForeignExchangeTemplateDescriptor(
    MonetaryAmount transactionAmount,
    MonetaryAmount functionalAmount,
    QuotedExchangeRateTemplateDescriptor quotedRate,
    ForeignExchangeTreatmentKind treatmentKind)
    implements TemplateDescriptorType {
  /** Validates one foreign-exchange template descriptor payload. */
  public ForeignExchangeTemplateDescriptor {
    transactionAmount =
        ContractDescriptorValidation.requireValue(transactionAmount, "transactionAmount");
    functionalAmount =
        ContractDescriptorValidation.requireValue(functionalAmount, "functionalAmount");
    quotedRate = ContractDescriptorValidation.requireValue(quotedRate, "quotedRate");
    treatmentKind = ContractDescriptorValidation.requireValue(treatmentKind, "treatmentKind");
    if (!transactionAmount.toMoney().isPositive()) {
      throw new IllegalArgumentException("transactionAmount must carry one positive amount.");
    }
    if (!functionalAmount.toMoney().isPositive()) {
      throw new IllegalArgumentException("functionalAmount must carry one positive amount.");
    }
  }
}
