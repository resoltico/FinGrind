package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.discovery.ContractReversalTemplates.ReversalTemplateDescriptor;
import java.util.List;
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
    ContractPostingRequestTemplateFieldSupport.forbidInventoryMaintenanceFields(fields, "journal");
    ContractPostingTemplateScalarFieldRules.forbidAmount(fields.amount(), "journal");
    ContractPostingTemplateScalarFieldRules.forbidQuantity(fields.quantity(), "journal");
    ContractPostingTemplateScalarFieldRules.forbidUnitCost(fields.unitCost(), "journal");
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
    ContractPostingRequestTemplateFieldSupport.forbidInventoryMaintenanceFields(
        fields, "openingPosition");
    ContractPostingTemplateFieldRules.forbidLines(fields.lines());
    ContractPostingTemplateScalarFieldRules.forbidAmount(fields.amount(), "openingPosition");
    ContractPostingTemplateScalarFieldRules.forbidQuantity(fields.quantity(), "openingPosition");
    ContractPostingTemplateScalarFieldRules.forbidUnitCost(fields.unitCost(), "openingPosition");
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
    ContractPostingRequestTemplateFieldSupport.forbidInventoryMaintenanceFields(fields, "reversal");
    ContractPostingTemplateScalarFieldRules.forbidAmount(fields.amount(), "reversal");
    ContractPostingTemplateScalarFieldRules.forbidQuantity(fields.quantity(), "reversal");
    ContractPostingTemplateScalarFieldRules.forbidUnitCost(fields.unitCost(), "reversal");
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

  static void validateInventoryShrinkageTemplate(
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    ContractPostingRequestTemplateFieldSupport.requireTextFields(
        fields,
        List.of(
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.SHRINKAGE_LOSS));
    ContractPostingRequestTemplateFieldSupport.forbidTextFields(
        fields,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.WRITE_DOWN_LOSS,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.COUNT_GAIN,
        ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY);
    ContractPostingTemplateScalarFieldRules.forbidAmount(fields.amount(), "inventoryShrinkage");
    ContractPostingTemplateScalarFieldRules.requirePositiveQuantity(fields.quantity());
    ContractPostingTemplateScalarFieldRules.forbidUnitCost(fields.unitCost(), "inventoryShrinkage");
    ContractPostingRequestTemplateFieldSupport.validateInventoryRelief(
        fields.inventoryRelief(),
        "inventoryShrinkage",
        ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN);
    ContractPostingRequestTemplateFieldSupport.requireNoSettlementAdjunct(
        fields, "inventoryShrinkage");
    ContractPostingTemplateFieldRules.forbidTax(fields.tax(), "inventoryShrinkage");
    ContractPostingTemplateFieldRules.forbidForeignExchange(
        fields.foreignExchange(), "inventoryShrinkage");
    ContractPostingRequestTemplateFieldSupport.forbidLinesAndOpeningBalances(fields);
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }
}
