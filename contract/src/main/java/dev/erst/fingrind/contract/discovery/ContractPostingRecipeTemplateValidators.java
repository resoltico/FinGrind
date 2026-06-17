package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.JournalRecipeKind;
import dev.erst.fingrind.contract.discovery.ContractTemplates.ReversalTemplateDescriptor;
import org.jspecify.annotations.Nullable;

/** Recipe-specific posting-template validators layered on top of the journal surface owner. */
final class ContractPostingRecipeTemplateValidators {
  private ContractPostingRecipeTemplateValidators() {}

  static void validate(
      JournalRecipeKind recipeKind,
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    if (recipeKind == JournalRecipeKind.CASH_REVENUE) {
      validateCashRevenue(fields, reversal);
      return;
    }
    if (recipeKind == JournalRecipeKind.CASH_EXPENSE) {
      validateCashExpense(fields, reversal);
      return;
    }
    if (recipeKind == JournalRecipeKind.EQUITY_CONTRIBUTION) {
      validateEquityContribution(fields, reversal);
      return;
    }
    validateEquityWithdrawal(fields, reversal);
  }

  private static void validateCashRevenue(
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    ContractPostingTemplateFieldRules.requireText(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateFieldRules.requireText(
        fields.revenueAccountCode(), "revenueAccountCode");
    ContractPostingTemplateFieldRules.requirePositiveAmount(fields.amount());
    ContractPostingTemplateFieldRules.forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.equityAccountCode(), "equityAccountCode");
    ContractPostingTemplateFieldRules.forbidLines(fields.lines());
    ContractPostingTemplateFieldRules.forbidOpeningBalances(fields.openingBalances());
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static void validateCashExpense(
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    ContractPostingTemplateFieldRules.requireText(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateFieldRules.requireText(
        fields.expenseAccountCode(), "expenseAccountCode");
    ContractPostingTemplateFieldRules.requirePositiveAmount(fields.amount());
    ContractPostingTemplateFieldRules.forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.equityAccountCode(), "equityAccountCode");
    ContractPostingTemplateFieldRules.forbidLines(fields.lines());
    ContractPostingTemplateFieldRules.forbidOpeningBalances(fields.openingBalances());
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static void validateEquityContribution(
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    ContractPostingTemplateFieldRules.requireText(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateFieldRules.requireText(fields.equityAccountCode(), "equityAccountCode");
    ContractPostingTemplateFieldRules.requirePositiveAmount(fields.amount());
    ContractPostingTemplateFieldRules.forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    ContractPostingTemplateFieldRules.forbidLines(fields.lines());
    ContractPostingTemplateFieldRules.forbidOpeningBalances(fields.openingBalances());
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static void validateEquityWithdrawal(
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    ContractPostingTemplateFieldRules.requireText(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateFieldRules.requireText(fields.equityAccountCode(), "equityAccountCode");
    ContractPostingTemplateFieldRules.requirePositiveAmount(fields.amount());
    ContractPostingTemplateFieldRules.forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    ContractPostingTemplateFieldRules.forbidLines(fields.lines());
    ContractPostingTemplateFieldRules.forbidOpeningBalances(fields.openingBalances());
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }
}
