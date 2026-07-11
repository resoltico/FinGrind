package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;

/** Posting-template validation rules owned by inventory acquisition and maintenance variants. */
final class ContractInventoryPostingRequestTemplateValidators {
  private static final ContractPostingRequestTemplateValidators
          .RoleQuantityUnitCostTemplateValidationRule
      PURCHASE_SETTLED_RULE =
          new ContractPostingRequestTemplateValidators.RoleQuantityUnitCostTemplateValidationRule(
              "purchaseSettled",
              true,
              true,
              List.of(
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY),
              List.of(
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.WRITE_DOWN_LOSS,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.SHRINKAGE_LOSS,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.COUNT_GAIN,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY));
  private static final ContractPostingRequestTemplateValidators
          .RoleQuantityUnitCostTemplateValidationRule
      PURCHASE_ON_CREDIT_RULE =
          new ContractPostingRequestTemplateValidators.RoleQuantityUnitCostTemplateValidationRule(
              "purchaseOnCredit",
              false,
              true,
              List.of(
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE),
              List.of(
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.WRITE_DOWN_LOSS,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.SHRINKAGE_LOSS,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.COUNT_GAIN,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY));
  private static final ContractPostingRequestTemplateValidators.RoleAmountTemplateValidationRule
      INVENTORY_CAPITALIZATION_SETTLED_RULE =
          new ContractPostingRequestTemplateValidators.RoleAmountTemplateValidationRule(
              "inventoryCapitalizationSettled",
              ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN,
              false,
              true,
              true,
              List.of(
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH),
              List.of(
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.WRITE_DOWN_LOSS,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.SHRINKAGE_LOSS,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.COUNT_GAIN,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY));
  private static final ContractPostingRequestTemplateValidators.RoleAmountTemplateValidationRule
      INVENTORY_CAPITALIZATION_ON_CREDIT_RULE =
          new ContractPostingRequestTemplateValidators.RoleAmountTemplateValidationRule(
              "inventoryCapitalizationOnCredit",
              ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN,
              false,
              false,
              true,
              List.of(
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE),
              List.of(
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.WRITE_DOWN_LOSS,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.SHRINKAGE_LOSS,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.COUNT_GAIN,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY));
  private static final ContractPostingRequestTemplateValidators.RoleAmountTemplateValidationRule
      INVENTORY_WRITE_DOWN_RULE =
          new ContractPostingRequestTemplateValidators.RoleAmountTemplateValidationRule(
              "inventoryWriteDown",
              ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN,
              false,
              false,
              false,
              List.of(
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.WRITE_DOWN_LOSS),
              List.of(
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.SHRINKAGE_LOSS,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.COUNT_GAIN,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY));
  private static final ContractPostingRequestTemplateValidators
          .RoleQuantityUnitCostTemplateValidationRule
      INVENTORY_COUNT_INCREASE_RULE =
          new ContractPostingRequestTemplateValidators.RoleQuantityUnitCostTemplateValidationRule(
              "inventoryCountIncrease",
              false,
              false,
              List.of(
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.COUNT_GAIN),
              List.of(
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.WRITE_DOWN_LOSS,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.SHRINKAGE_LOSS,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY));

  private ContractInventoryPostingRequestTemplateValidators() {}

  static Map<
          BookkeepingEntryKind, ContractPostingRequestTemplateValidators.PostingTemplateValidator>
      validators() {
    return Map.of(
        BookkeepingEntryKind.PURCHASE_SETTLED,
        ContractPostingRequestTemplateValidators.purchaseValidator(PURCHASE_SETTLED_RULE),
        BookkeepingEntryKind.PURCHASE_ON_CREDIT,
        ContractPostingRequestTemplateValidators.purchaseValidator(PURCHASE_ON_CREDIT_RULE),
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED,
        ContractPostingRequestTemplateValidators.roleAmountValidator(
            INVENTORY_CAPITALIZATION_SETTLED_RULE),
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
        ContractPostingRequestTemplateValidators.roleAmountValidator(
            INVENTORY_CAPITALIZATION_ON_CREDIT_RULE),
        BookkeepingEntryKind.INVENTORY_WRITE_DOWN,
        ContractPostingRequestTemplateValidators.roleAmountValidator(INVENTORY_WRITE_DOWN_RULE),
        BookkeepingEntryKind.INVENTORY_SHRINKAGE,
        ContractPostingRequestTemplateSpecialCaseValidators::validateInventoryShrinkageTemplate,
        BookkeepingEntryKind.INVENTORY_COUNT_INCREASE,
        ContractPostingRequestTemplateValidators.purchaseValidator(INVENTORY_COUNT_INCREASE_RULE));
  }
}
