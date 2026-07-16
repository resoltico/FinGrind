package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import org.jspecify.annotations.Nullable;

/** Financing request-template facts exposed by the discovery contract. */
public interface ContractFinancingTemplates {
  /** Context-specific fields for one financing posting template. */
  record FinancingTemplateDescriptor(
      @Nullable String financingArrangementId,
      @Nullable String principalLiabilityAccountCode,
      @Nullable String interestPayableAccountCode,
      @Nullable String interestExpenseAccountCode,
      @Nullable MonetaryAmount principalAmount,
      @Nullable MonetaryAmount interestAmount)
      implements TemplateDescriptorType {
    public FinancingTemplateDescriptor {
      financingArrangementId =
          ContractDescriptorValidation.requireOptionalText(
              financingArrangementId, "financingArrangementId");
      principalLiabilityAccountCode =
          ContractDescriptorValidation.requireOptionalText(
              principalLiabilityAccountCode, "principalLiabilityAccountCode");
      interestPayableAccountCode =
          ContractDescriptorValidation.requireOptionalText(
              interestPayableAccountCode, "interestPayableAccountCode");
      interestExpenseAccountCode =
          ContractDescriptorValidation.requireOptionalText(
              interestExpenseAccountCode, "interestExpenseAccountCode");
      principalAmount =
          ContractDescriptorValidation.requireOptionalValue(principalAmount, "principalAmount");
      interestAmount =
          ContractDescriptorValidation.requireOptionalValue(interestAmount, "interestAmount");
    }
  }
}
