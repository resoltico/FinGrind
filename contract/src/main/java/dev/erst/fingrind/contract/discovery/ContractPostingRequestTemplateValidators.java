package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractTemplates.JournalLineTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.OpeningBalanceTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.ReversalTemplateDescriptor;
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
  private static final RoleAmountTemplateValidationRule PURCHASE_SETTLED_RULE =
      new RoleAmountTemplateValidationRule(
          "purchaseSettled",
          ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN,
          false,
          true,
          false,
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY),
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY));
  private static final RoleAmountTemplateValidationRule PURCHASE_ON_CREDIT_RULE =
      new RoleAmountTemplateValidationRule(
          "purchaseOnCredit",
          ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN,
          false,
          false,
          false,
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE),
          List.of(
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
              ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
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
      Map.ofEntries(
          Map.entry(
              BookkeepingEntryKind.DIRECT_JOURNAL,
              ContractPostingRequestTemplateSpecialCaseValidators::validateDirectJournalTemplate),
          Map.entry(BookkeepingEntryKind.SALE_SETTLED, roleAmountValidator(SALE_SETTLED_RULE)),
          Map.entry(BookkeepingEntryKind.SALE_ON_CREDIT, roleAmountValidator(SALE_ON_CREDIT_RULE)),
          Map.entry(
              BookkeepingEntryKind.PURCHASE_SETTLED, roleAmountValidator(PURCHASE_SETTLED_RULE)),
          Map.entry(
              BookkeepingEntryKind.PURCHASE_ON_CREDIT,
              roleAmountValidator(PURCHASE_ON_CREDIT_RULE)),
          Map.entry(
              BookkeepingEntryKind.EXPENSE_SETTLED, roleAmountValidator(EXPENSE_SETTLED_RULE)),
          Map.entry(
              BookkeepingEntryKind.EXPENSE_ON_CREDIT, roleAmountValidator(EXPENSE_ON_CREDIT_RULE)),
          Map.entry(BookkeepingEntryKind.RECEIPT, roleAmountValidator(RECEIPT_RULE)),
          Map.entry(BookkeepingEntryKind.PAYMENT, roleAmountValidator(PAYMENT_RULE)),
          Map.entry(
              BookkeepingEntryKind.OWNER_CONTRIBUTION,
              roleAmountValidator(OWNER_CONTRIBUTION_RULE)),
          Map.entry(
              BookkeepingEntryKind.OWNER_WITHDRAWAL, roleAmountValidator(OWNER_WITHDRAWAL_RULE)),
          Map.entry(
              BookkeepingEntryKind.OPENING_POSITION,
              ContractPostingRequestTemplateSpecialCaseValidators::validateOpeningPositionTemplate),
          Map.entry(
              BookkeepingEntryKind.REVERSAL,
              ContractPostingRequestTemplateSpecialCaseValidators::validateReversalTemplate));

  private ContractPostingRequestTemplateValidators() {}

  static void validate(
      BookkeepingEntryKind entryKind,
      PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    Objects.requireNonNull(entryKind, "entryKind");
    Objects.requireNonNull(VALIDATORS.get(entryKind), "entryKind").validate(fields, reversal);
  }

  private static void validateRoleAmountTemplate(
      PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal,
      RoleAmountTemplateValidationRule rule) {
    ContractPostingRequestTemplateFieldSupport.requireTextFields(fields, rule.requiredFields());
    ContractPostingTemplateFieldRules.requirePositiveAmount(fields.amount());
    ContractPostingRequestTemplateFieldSupport.validateInventoryRelief(
        fields.inventoryRelief(), rule.owner(), rule.inventoryReliefPolicy());
    ContractPostingRequestTemplateFieldSupport.forbidTextFields(fields, rule.forbiddenFields());
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

  private static PostingTemplateValidator roleAmountValidator(
      RoleAmountTemplateValidationRule rule) {
    return (fields, reversal) -> validateRoleAmountTemplate(fields, reversal, rule);
  }

  record PostingTemplateFields(
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String payableAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String inventoryAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String equityAccountCode,
      @Nullable MonetaryAmount amount,
      @Nullable InventoryReliefTemplateDescriptor inventoryRelief,
      @Nullable SettlementAdjunctTemplateDescriptor settlementAdjunct,
      @Nullable ForeignExchangeTemplateDescriptor foreignExchange,
      @Nullable TaxSelectionTemplateDescriptor tax,
      @Nullable List<JournalLineTemplateDescriptor> lines,
      @Nullable List<OpeningBalanceTemplateDescriptor> openingBalances) {}

  /** Variant-specific validation contract for one posting-template kind. */
  @FunctionalInterface
  private interface PostingTemplateValidator {
    /** Validates one posting-template field bundle for one entry kind. */
    void validate(PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal);
  }

  private record RoleAmountTemplateValidationRule(
      String owner,
      ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy inventoryReliefPolicy,
      boolean settlementAdjunctAllowed,
      boolean foreignExchangeAllowed,
      boolean taxAllowed,
      List<ContractPostingRequestTemplateFieldSupport.TemplateTextField> requiredFields,
      List<ContractPostingRequestTemplateFieldSupport.TemplateTextField> forbiddenFields) {}
}
