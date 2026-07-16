package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateFieldSupport.TemplateTextField;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateValidators.PostingTemplateValidator;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateValidators.RoleAmountTemplateValidationRule;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;

/** Owns template validation rules for standard sales, expenses, settlements, and owner events. */
final class ContractStandardPostingRequestTemplateValidators {
  private static final RoleAmountTemplateValidationRule SALE_SETTLED_RULE =
      roleAmountRule(
          "saleSettled",
          InventoryReliefPolicy.OPTIONAL,
          false,
          true,
          true,
          List.of(TemplateTextField.CASH, TemplateTextField.REVENUE),
          List.of(
              TemplateTextField.RECEIVABLE,
              TemplateTextField.PAYABLE,
              TemplateTextField.INVENTORY,
              TemplateTextField.EXPENSE,
              TemplateTextField.EQUITY));
  private static final RoleAmountTemplateValidationRule SALE_ON_CREDIT_RULE =
      roleAmountRule(
          "saleOnCredit",
          InventoryReliefPolicy.OPTIONAL,
          false,
          false,
          true,
          List.of(TemplateTextField.RECEIVABLE, TemplateTextField.REVENUE),
          List.of(
              TemplateTextField.CASH,
              TemplateTextField.PAYABLE,
              TemplateTextField.INVENTORY,
              TemplateTextField.EXPENSE,
              TemplateTextField.EQUITY));
  private static final RoleAmountTemplateValidationRule EXPENSE_SETTLED_RULE =
      roleAmountRule(
          "expenseSettled",
          InventoryReliefPolicy.FORBIDDEN,
          false,
          true,
          true,
          List.of(TemplateTextField.CASH, TemplateTextField.EXPENSE),
          List.of(
              TemplateTextField.RECEIVABLE,
              TemplateTextField.PAYABLE,
              TemplateTextField.REVENUE,
              TemplateTextField.INVENTORY,
              TemplateTextField.EQUITY));
  private static final RoleAmountTemplateValidationRule EXPENSE_ON_CREDIT_RULE =
      roleAmountRule(
          "expenseOnCredit",
          InventoryReliefPolicy.FORBIDDEN,
          false,
          false,
          true,
          List.of(TemplateTextField.EXPENSE, TemplateTextField.PAYABLE),
          List.of(
              TemplateTextField.CASH,
              TemplateTextField.RECEIVABLE,
              TemplateTextField.REVENUE,
              TemplateTextField.INVENTORY,
              TemplateTextField.EQUITY));
  private static final RoleAmountTemplateValidationRule RECEIPT_RULE =
      roleAmountRule(
          "receipt",
          InventoryReliefPolicy.FORBIDDEN,
          true,
          false,
          false,
          List.of(TemplateTextField.CASH, TemplateTextField.RECEIVABLE),
          List.of(
              TemplateTextField.PAYABLE,
              TemplateTextField.REVENUE,
              TemplateTextField.INVENTORY,
              TemplateTextField.EXPENSE,
              TemplateTextField.EQUITY));
  private static final RoleAmountTemplateValidationRule PAYMENT_RULE =
      roleAmountRule(
          "payment",
          InventoryReliefPolicy.FORBIDDEN,
          true,
          false,
          false,
          List.of(TemplateTextField.CASH, TemplateTextField.PAYABLE),
          List.of(
              TemplateTextField.RECEIVABLE,
              TemplateTextField.REVENUE,
              TemplateTextField.INVENTORY,
              TemplateTextField.EXPENSE,
              TemplateTextField.EQUITY));
  private static final RoleAmountTemplateValidationRule OWNER_CONTRIBUTION_RULE =
      roleAmountRule(
          "ownerContribution",
          InventoryReliefPolicy.FORBIDDEN,
          false,
          true,
          false,
          List.of(TemplateTextField.CASH, TemplateTextField.EQUITY),
          List.of(
              TemplateTextField.RECEIVABLE,
              TemplateTextField.PAYABLE,
              TemplateTextField.REVENUE,
              TemplateTextField.INVENTORY,
              TemplateTextField.EXPENSE));
  private static final RoleAmountTemplateValidationRule OWNER_WITHDRAWAL_RULE =
      roleAmountRule(
          "ownerWithdrawal",
          InventoryReliefPolicy.FORBIDDEN,
          false,
          true,
          false,
          List.of(TemplateTextField.CASH, TemplateTextField.EQUITY),
          List.of(
              TemplateTextField.RECEIVABLE,
              TemplateTextField.PAYABLE,
              TemplateTextField.REVENUE,
              TemplateTextField.INVENTORY,
              TemplateTextField.EXPENSE));

  private ContractStandardPostingRequestTemplateValidators() {}

  static Map<BookkeepingEntryKind, PostingTemplateValidator> validators() {
    return Map.of(
        BookkeepingEntryKind.SALE_SETTLED,
        ContractPostingRequestTemplateValidators.roleAmountValidator(SALE_SETTLED_RULE),
        BookkeepingEntryKind.SALE_ON_CREDIT,
        ContractPostingRequestTemplateValidators.roleAmountValidator(SALE_ON_CREDIT_RULE),
        BookkeepingEntryKind.EXPENSE_SETTLED,
        ContractPostingRequestTemplateValidators.roleAmountValidator(EXPENSE_SETTLED_RULE),
        BookkeepingEntryKind.EXPENSE_ON_CREDIT,
        ContractPostingRequestTemplateValidators.roleAmountValidator(EXPENSE_ON_CREDIT_RULE),
        BookkeepingEntryKind.RECEIPT,
        ContractPostingRequestTemplateValidators.roleAmountValidator(RECEIPT_RULE),
        BookkeepingEntryKind.PAYMENT,
        ContractPostingRequestTemplateValidators.roleAmountValidator(PAYMENT_RULE),
        BookkeepingEntryKind.OWNER_CONTRIBUTION,
        ContractPostingRequestTemplateValidators.roleAmountValidator(OWNER_CONTRIBUTION_RULE),
        BookkeepingEntryKind.OWNER_WITHDRAWAL,
        ContractPostingRequestTemplateValidators.roleAmountValidator(OWNER_WITHDRAWAL_RULE));
  }

  private static RoleAmountTemplateValidationRule roleAmountRule(
      String owner,
      InventoryReliefPolicy inventoryReliefPolicy,
      boolean settlementAdjunctAllowed,
      boolean foreignExchangeAllowed,
      boolean taxAllowed,
      List<TemplateTextField> requiredFields,
      List<TemplateTextField> forbiddenFields) {
    return new RoleAmountTemplateValidationRule(
        owner,
        inventoryReliefPolicy,
        settlementAdjunctAllowed,
        foreignExchangeAllowed,
        taxAllowed,
        requiredFields,
        forbiddenFields);
  }
}
