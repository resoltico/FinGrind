package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import org.jspecify.annotations.Nullable;

/** Fixed-asset request-template facts exposed by the discovery contract. */
public interface ContractFixedAssetTemplates {
  /** Context-specific fields for one fixed-asset posting template. */
  record FixedAssetTemplateDescriptor(
      @Nullable String fixedAssetId,
      @Nullable String assetAccountCode,
      @Nullable String accumulatedDepreciationAccountCode,
      @Nullable String depreciationExpenseAccountCode,
      @Nullable String disposalGainAccountCode,
      @Nullable String disposalLossAccountCode,
      @Nullable MonetaryAmount cost,
      @Nullable FixedAssetDepreciationScheduleTemplateDescriptor depreciationSchedule,
      @Nullable MonetaryAmount proceeds)
      implements TemplateDescriptorType {
    public FixedAssetTemplateDescriptor {
      fixedAssetId = ContractDescriptorValidation.requireOptionalText(fixedAssetId, "fixedAssetId");
      assetAccountCode =
          ContractDescriptorValidation.requireOptionalText(assetAccountCode, "assetAccountCode");
      accumulatedDepreciationAccountCode =
          ContractDescriptorValidation.requireOptionalText(
              accumulatedDepreciationAccountCode, "accumulatedDepreciationAccountCode");
      depreciationExpenseAccountCode =
          ContractDescriptorValidation.requireOptionalText(
              depreciationExpenseAccountCode, "depreciationExpenseAccountCode");
      disposalGainAccountCode =
          ContractDescriptorValidation.requireOptionalText(
              disposalGainAccountCode, "disposalGainAccountCode");
      disposalLossAccountCode =
          ContractDescriptorValidation.requireOptionalText(
              disposalLossAccountCode, "disposalLossAccountCode");
      cost = ContractDescriptorValidation.requireOptionalValue(cost, "cost");
      depreciationSchedule =
          ContractDescriptorValidation.requireOptionalValue(
              depreciationSchedule, "depreciationSchedule");
      proceeds = ContractDescriptorValidation.requireOptionalValue(proceeds, "proceeds");
    }
  }

  /** Straight-line terms retained with a capitalized fixed-asset template. */
  record FixedAssetDepreciationScheduleTemplateDescriptor(
      String inServiceDate, int usefulLifeMonths, MonetaryAmount residualValue)
      implements TemplateDescriptorType {
    public FixedAssetDepreciationScheduleTemplateDescriptor {
      inServiceDate = ContractDescriptorValidation.requireText(inServiceDate, "inServiceDate");
      if (usefulLifeMonths < 1 || usefulLifeMonths > 1_200) {
        throw new IllegalArgumentException("usefulLifeMonths must be between 1 and 1200.");
      }
      residualValue = ContractDescriptorValidation.requireValue(residualValue, "residualValue");
    }
  }
}
