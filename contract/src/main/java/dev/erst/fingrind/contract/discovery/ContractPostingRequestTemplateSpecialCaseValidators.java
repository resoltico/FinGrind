package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.discovery.ContractTemplates.ReversalTemplateDescriptor;
import org.jspecify.annotations.Nullable;

/** Validation owners for posting-template variants that are not role-and-amount shaped. */
final class ContractPostingRequestTemplateSpecialCaseValidators {
  private ContractPostingRequestTemplateSpecialCaseValidators() {}

  static void validateDirectJournalTemplate(
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    ContractPostingTemplateFieldRules.requireLines(fields.lines(), "journal");
    ContractPostingRequestTemplateFieldSupport.forbidTextFields(
        fields,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY);
    ContractPostingTemplateFieldRules.forbidAmount(fields.amount(), "journal");
    ContractPostingRequestTemplateFieldSupport.requireNoSettlementAdjunct(fields, "journal");
    ContractPostingTemplateFieldRules.forbidTax(fields.tax(), "journal");
    ContractPostingTemplateFieldRules.forbidOpeningBalances(fields.openingBalances());
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  static void validateOpeningPositionTemplate(
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    if (fields.openingBalances() == null || fields.openingBalances().size() < 2) {
      throw new IllegalArgumentException(
          "openingBalances must contain at least two opening balances for openingPosition.");
    }
    ContractPostingRequestTemplateFieldSupport.forbidTextFields(
        fields,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY);
    ContractPostingTemplateFieldRules.forbidLines(fields.lines());
    ContractPostingTemplateFieldRules.forbidAmount(fields.amount(), "openingPosition");
    ContractPostingRequestTemplateFieldSupport.validateInventoryRelief(
        fields.inventoryRelief(),
        "openingPosition",
        ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN);
    ContractPostingRequestTemplateFieldSupport.requireNoSettlementAdjunct(
        fields, "openingPosition");
    ContractPostingTemplateFieldRules.forbidTax(fields.tax(), "openingPosition");
    ContractPostingTemplateFieldRules.forbidForeignExchange(
        fields.foreignExchange(), "openingPosition");
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  static void validateReversalTemplate(
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    ContractPostingRequestTemplateFieldSupport.forbidLinesAndOpeningBalances(fields);
    ContractPostingRequestTemplateFieldSupport.forbidTextFields(
        fields,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY);
    ContractPostingTemplateFieldRules.forbidAmount(fields.amount(), "reversal");
    ContractPostingRequestTemplateFieldSupport.validateInventoryRelief(
        fields.inventoryRelief(),
        "reversal",
        ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN);
    ContractPostingRequestTemplateFieldSupport.requireNoSettlementAdjunct(fields, "reversal");
    ContractPostingTemplateFieldRules.forbidTax(fields.tax(), "reversal");
    if (reversal == null) {
      throw new IllegalArgumentException("reversal must be present for reversal.");
    }
  }
}
