package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import org.jspecify.annotations.Nullable;

/** Realized foreign-exchange request-template facts exposed by the discovery contract. */
public interface ContractRealizedForeignExchangeTemplates {
  /** Context-specific fields for one realized foreign-exchange posting template. */
  record RealizedForeignExchangeTemplateDescriptor(
      @Nullable String foreignCurrencyObligationId,
      @Nullable String realizedGainAccountCode,
      @Nullable String realizedLossAccountCode)
      implements TemplateDescriptorType {
    public RealizedForeignExchangeTemplateDescriptor {
      foreignCurrencyObligationId =
          ContractDescriptorValidation.requireOptionalText(
              foreignCurrencyObligationId, "foreignCurrencyObligationId");
      realizedGainAccountCode =
          ContractDescriptorValidation.requireOptionalText(
              realizedGainAccountCode, "realizedGainAccountCode");
      realizedLossAccountCode =
          ContractDescriptorValidation.requireOptionalText(
              realizedLossAccountCode, "realizedLossAccountCode");
    }
  }
}
