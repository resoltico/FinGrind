package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.discovery.ContractFixedAssetTemplates.FixedAssetTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateFieldSupport.TemplateTextField;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateValidators.PostingTemplateFields;
import dev.erst.fingrind.contract.discovery.ContractReversalTemplates.ReversalTemplateDescriptor;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Validates discovery templates owned by the Fixed Assets context. */
final class ContractFixedAssetPostingRequestTemplateValidators {
  private ContractFixedAssetPostingRequestTemplateValidators() {}

  static void validateCapitalizationTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    FixedAssetTemplateDescriptor fixedAsset =
        requiredFixedAsset(fields, "fixed-asset capitalization");
    ContractPostingTemplateFieldRules.requirePresent(fixedAsset.fixedAssetId(), "fixedAssetId");
    ContractPostingTemplateFieldRules.requirePresent(
        fixedAsset.assetAccountCode(), "assetAccountCode");
    ContractPostingTemplateFieldRules.requirePresent(
        fixedAsset.accumulatedDepreciationAccountCode(), "accumulatedDepreciationAccountCode");
    ContractPostingTemplateFieldRules.requirePresent(
        fixedAsset.depreciationExpenseAccountCode(), "depreciationExpenseAccountCode");
    ContractPostingTemplateFieldRules.requirePresent(
        fixedAsset.disposalGainAccountCode(), "disposalGainAccountCode");
    ContractPostingTemplateFieldRules.requirePresent(
        fixedAsset.disposalLossAccountCode(), "disposalLossAccountCode");
    ContractPostingTemplateFieldRules.requirePresent(fixedAsset.cost(), "cost");
    ContractPostingTemplateFieldRules.requirePresent(
        fixedAsset.depreciationSchedule(), "depreciationSchedule");
    ContractPostingTemplateFieldRules.requireAbsent(fixedAsset.proceeds(), "proceeds");
    ContractPostingTemplateFieldRules.requirePresent(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateNonTextFieldPolicy.requireOnlyOperationalFields(
        fields,
        List.of(TemplateTextField.CASH),
        ContractPostingTemplateNonTextFieldPolicy.ContextField.FIXED_ASSET);
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  static void validateDepreciationTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    FixedAssetTemplateDescriptor fixedAsset =
        requiredFixedAsset(fields, "fixed-asset depreciation");
    ContractPostingTemplateFieldRules.requirePresent(fixedAsset.fixedAssetId(), "fixedAssetId");
    ContractPostingTemplateFieldRules.requireAbsent(
        fixedAsset.assetAccountCode(), "assetAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(
        fixedAsset.accumulatedDepreciationAccountCode(), "accumulatedDepreciationAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(
        fixedAsset.depreciationExpenseAccountCode(), "depreciationExpenseAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(
        fixedAsset.disposalGainAccountCode(), "disposalGainAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(
        fixedAsset.disposalLossAccountCode(), "disposalLossAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(fixedAsset.cost(), "cost");
    ContractPostingTemplateFieldRules.requireAbsent(
        fixedAsset.depreciationSchedule(), "depreciationSchedule");
    ContractPostingTemplateFieldRules.requireAbsent(fixedAsset.proceeds(), "proceeds");
    ContractPostingTemplateFieldRules.requireAbsent(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateNonTextFieldPolicy.requireOnlyOperationalFields(
        fields, List.of(), ContractPostingTemplateNonTextFieldPolicy.ContextField.FIXED_ASSET);
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  static void validateDisposalTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    FixedAssetTemplateDescriptor fixedAsset = requiredFixedAsset(fields, "fixed-asset disposal");
    ContractPostingTemplateFieldRules.requirePresent(fixedAsset.fixedAssetId(), "fixedAssetId");
    ContractPostingTemplateFieldRules.requireAbsent(
        fixedAsset.assetAccountCode(), "assetAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(
        fixedAsset.accumulatedDepreciationAccountCode(), "accumulatedDepreciationAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(
        fixedAsset.depreciationExpenseAccountCode(), "depreciationExpenseAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(
        fixedAsset.disposalGainAccountCode(), "disposalGainAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(
        fixedAsset.disposalLossAccountCode(), "disposalLossAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(fixedAsset.cost(), "cost");
    ContractPostingTemplateFieldRules.requireAbsent(
        fixedAsset.depreciationSchedule(), "depreciationSchedule");
    ContractPostingTemplateFieldRules.requirePresent(fixedAsset.proceeds(), "proceeds");
    ContractPostingTemplateFieldRules.requirePresent(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateNonTextFieldPolicy.requireOnlyOperationalFields(
        fields,
        List.of(TemplateTextField.CASH),
        ContractPostingTemplateNonTextFieldPolicy.ContextField.FIXED_ASSET);
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static FixedAssetTemplateDescriptor requiredFixedAsset(
      PostingTemplateFields fields, String owner) {
    return Objects.requireNonNull(
        fields.fixedAsset(), () -> "fixedAsset is required for " + owner + ".");
  }
}
