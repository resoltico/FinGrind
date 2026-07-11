package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractReversalTemplates.ReversalTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.JournalLineTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.OpeningBalanceTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.SettlementAdjunctTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.TaxSelectionTemplateDescriptor;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Posting-template validator owner for the discovery contract namespace. */
final class ContractPostingRequestTemplateValidators {
  private static final RoleAmountTemplateValidationRule SALE_SETTLED_RULE =
      new RoleAmountTemplateValidationRule(
          "saleSettled",
          ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.OPTIONAL,
          false,
          true,
          true,
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE),
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY));
  private static final RoleAmountTemplateValidationRule SALE_ON_CREDIT_RULE =
      new RoleAmountTemplateValidationRule(
          "saleOnCredit",
          ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.OPTIONAL,
          false,
          false,
          true,
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE),
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY));
  private static final RoleAmountTemplateValidationRule EXPENSE_SETTLED_RULE =
      new RoleAmountTemplateValidationRule(
          "expenseSettled",
          ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN,
          false,
          true,
          true,
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE),
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY));
  private static final RoleAmountTemplateValidationRule EXPENSE_ON_CREDIT_RULE =
      new RoleAmountTemplateValidationRule(
          "expenseOnCredit",
          ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN,
          false,
          false,
          true,
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE),
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY));
  private static final RoleAmountTemplateValidationRule RECEIPT_RULE =
      new RoleAmountTemplateValidationRule(
          "receipt",
          ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN,
          true,
          false,
          false,
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE),
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY));
  private static final RoleAmountTemplateValidationRule PAYMENT_RULE =
      new RoleAmountTemplateValidationRule(
          "payment",
          ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN,
          true,
          false,
          false,
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE),
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY));
  private static final RoleAmountTemplateValidationRule OWNER_CONTRIBUTION_RULE =
      new RoleAmountTemplateValidationRule(
          "ownerContribution",
          ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN,
          false,
          true,
          false,
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY),
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE));
  private static final RoleAmountTemplateValidationRule OWNER_WITHDRAWAL_RULE =
      new RoleAmountTemplateValidationRule(
          "ownerWithdrawal",
          ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN,
          false,
          true,
          false,
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY),
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE));
  private static final Map<BookkeepingEntryKind, PostingTemplateValidator> VALIDATORS =
      validators();

  private ContractPostingRequestTemplateValidators() {}

  static void validate(
      BookkeepingEntryKind entryKind,
      PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    Objects.requireNonNull(entryKind, "entryKind");
    Objects.requireNonNull(VALIDATORS.get(entryKind), "entryKind").validate(fields, reversal);
  }

  static void validateRoleAmountTemplate(
      PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal,
      RoleAmountTemplateValidationRule rule) {
    ContractPostingRequestTemplateFieldSupport.requireTextFields(fields, rule.requiredFields());
    ContractPostingTemplateFieldRules.requirePositiveAmount(fields.amount());
    ContractPostingTemplateFieldRules.forbidQuantity(fields.quantity(), rule.owner());
    ContractPostingTemplateFieldRules.forbidUnitCost(fields.unitCost(), rule.owner());
    ContractPostingRequestTemplateFieldSupport.validateInventoryRelief(
        fields.inventoryRelief(), rule.owner(), rule.inventoryReliefPolicy());
    ContractPostingRequestTemplateFieldSupport.forbidTextFields(fields, rule.forbiddenFields());
    ContractPostingRequestTemplateFieldSupport.forbidInventoryMaintenanceFields(
        fields, rule.owner());
    if (!rule.settlementAdjunctAllowed()) {
      ContractPostingRequestTemplateFieldSupport.requireNoSettlementAdjunct(fields, rule.owner());
    }
    ContractPostingRequestTemplateFieldSupport.forbidLinesAndOpeningBalances(fields);
    if (!rule.taxAllowed()) {
      ContractPostingTemplateFieldRules.forbidTax(fields.tax(), rule.owner());
    }
    if (!rule.foreignExchangeAllowed()) {
      ContractPostingTemplateFieldRules.forbidForeignExchange(
          fields.foreignExchange(), rule.owner());
    }
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  static void validatePurchaseTemplate(
      PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal,
      RoleQuantityUnitCostTemplateValidationRule rule) {
    ContractPostingRequestTemplateFieldSupport.requireTextFields(fields, rule.requiredFields());
    ContractPostingRequestTemplateFieldSupport.forbidTextFields(fields, rule.forbiddenFields());
    ContractPostingRequestTemplateFieldSupport.forbidInventoryMaintenanceFields(
        fields, rule.owner());
    ContractPostingTemplateFieldRules.forbidAmount(fields.amount(), rule.owner());
    ContractPostingTemplateFieldRules.requirePositiveQuantity(fields.quantity());
    ContractPostingTemplateFieldRules.requirePositiveUnitCost(fields.unitCost());
    ContractPostingRequestTemplateFieldSupport.validateInventoryRelief(
        fields.inventoryRelief(),
        rule.owner(),
        ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN);
    ContractPostingRequestTemplateFieldSupport.requireNoSettlementAdjunct(fields, rule.owner());
    ContractPostingRequestTemplateFieldSupport.forbidLinesAndOpeningBalances(fields);
    if (!rule.taxAllowed()) {
      ContractPostingTemplateFieldRules.forbidTax(fields.tax(), rule.owner());
    }
    if (!rule.foreignExchangeAllowed()) {
      ContractPostingTemplateFieldRules.forbidForeignExchange(
          fields.foreignExchange(), rule.owner());
    }
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  static PostingTemplateValidator roleAmountValidator(RoleAmountTemplateValidationRule rule) {
    return (fields, reversal) -> validateRoleAmountTemplate(fields, reversal, rule);
  }

  static PostingTemplateValidator purchaseValidator(
      RoleQuantityUnitCostTemplateValidationRule rule) {
    return (fields, reversal) -> validatePurchaseTemplate(fields, reversal, rule);
  }

  record PostingTemplateFields(
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String payableAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String inventoryAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String writeDownLossAccountCode,
      @Nullable String shrinkageLossAccountCode,
      @Nullable String countGainAccountCode,
      @Nullable String equityAccountCode,
      @Nullable MonetaryAmount amount,
      @Nullable String quantity,
      @Nullable MonetaryAmount unitCost,
      @Nullable InventoryReliefTemplateDescriptor inventoryRelief,
      @Nullable SettlementAdjunctTemplateDescriptor settlementAdjunct,
      @Nullable ForeignExchangeTemplateDescriptor foreignExchange,
      @Nullable TaxSelectionTemplateDescriptor tax,
      @Nullable List<JournalLineTemplateDescriptor> lines,
      @Nullable List<OpeningBalanceTemplateDescriptor> openingBalances) {}

  /** Variant-specific validation contract for one posting-template kind. */
  @FunctionalInterface
  interface PostingTemplateValidator {
    /** Validates one posting-template field bundle for one entry kind. */
    void validate(PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal);
  }

  record RoleAmountTemplateValidationRule(
      String owner,
      ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy inventoryReliefPolicy,
      boolean settlementAdjunctAllowed,
      boolean foreignExchangeAllowed,
      boolean taxAllowed,
      List<ContractPostingRequestTemplateFieldSupport.TemplateTextField> requiredFields,
      List<ContractPostingRequestTemplateFieldSupport.TemplateTextField> forbiddenFields) {}

  record RoleQuantityUnitCostTemplateValidationRule(
      String owner,
      boolean foreignExchangeAllowed,
      boolean taxAllowed,
      List<ContractPostingRequestTemplateFieldSupport.TemplateTextField> requiredFields,
      List<ContractPostingRequestTemplateFieldSupport.TemplateTextField> forbiddenFields) {}

  private static Map<BookkeepingEntryKind, PostingTemplateValidator> validators() {
    var validators =
        new java.util.EnumMap<BookkeepingEntryKind, PostingTemplateValidator>(
            BookkeepingEntryKind.class);
    validators.put(
        BookkeepingEntryKind.DIRECT_JOURNAL,
        ContractPostingRequestTemplateSpecialCaseValidators::validateDirectJournalTemplate);
    validators.put(BookkeepingEntryKind.SALE_SETTLED, roleAmountValidator(SALE_SETTLED_RULE));
    validators.put(BookkeepingEntryKind.SALE_ON_CREDIT, roleAmountValidator(SALE_ON_CREDIT_RULE));
    validators.putAll(ContractInventoryPostingRequestTemplateValidators.validators());
    validators.put(BookkeepingEntryKind.EXPENSE_SETTLED, roleAmountValidator(EXPENSE_SETTLED_RULE));
    validators.put(
        BookkeepingEntryKind.EXPENSE_ON_CREDIT, roleAmountValidator(EXPENSE_ON_CREDIT_RULE));
    validators.put(BookkeepingEntryKind.RECEIPT, roleAmountValidator(RECEIPT_RULE));
    validators.put(BookkeepingEntryKind.PAYMENT, roleAmountValidator(PAYMENT_RULE));
    validators.put(
        BookkeepingEntryKind.OWNER_CONTRIBUTION, roleAmountValidator(OWNER_CONTRIBUTION_RULE));
    validators.put(
        BookkeepingEntryKind.OWNER_WITHDRAWAL, roleAmountValidator(OWNER_WITHDRAWAL_RULE));
    validators.put(
        BookkeepingEntryKind.OPENING_POSITION,
        ContractPostingRequestTemplateSpecialCaseValidators::validateOpeningPositionTemplate);
    validators.put(
        BookkeepingEntryKind.REVERSAL,
        ContractPostingRequestTemplateSpecialCaseValidators::validateReversalTemplate);
    return Map.copyOf(validators);
  }
}
