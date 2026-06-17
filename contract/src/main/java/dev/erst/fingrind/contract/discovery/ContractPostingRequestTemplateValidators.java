package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.JournalRecipeKind;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractTemplates.JournalLineTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.OpeningBalanceTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.ReversalTemplateDescriptor;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Posting-template validator owner for the discovery contract namespace. */
final class ContractPostingRequestTemplateValidators {
  private ContractPostingRequestTemplateValidators() {}

  static void validate(
      BookkeepingEntryKind entryKind,
      @Nullable JournalRecipeKind recipeKind,
      PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    if (entryKind == BookkeepingEntryKind.JOURNAL) {
      validateJournalTemplate(recipeKind, fields, reversal);
      return;
    }
    if (entryKind == BookkeepingEntryKind.OPEN_ACCOUNTING_POSITION) {
      validateOpenAccountingPositionTemplate(recipeKind, fields, reversal);
      return;
    }
    validateReversalAdjustmentTemplate(recipeKind, fields, reversal);
  }

  private static void validateJournalTemplate(
      @Nullable JournalRecipeKind recipeKind,
      PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    if (recipeKind == null) {
      validateDirectJournalTemplate(fields, reversal);
      return;
    }
    ContractPostingRecipeTemplateValidators.validate(recipeKind, fields, reversal);
  }

  private static void validateDirectJournalTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    ContractPostingTemplateFieldRules.requireLines(fields.lines(), "journal");
    ContractPostingTemplateFieldRules.forbidText(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.equityAccountCode(), "equityAccountCode");
    ContractPostingTemplateFieldRules.forbidAmount(fields.amount(), "journal");
    ContractPostingTemplateFieldRules.forbidOpeningBalances(fields.openingBalances());
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static void validateOpenAccountingPositionTemplate(
      @Nullable JournalRecipeKind recipeKind,
      PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    ContractPostingTemplateFieldRules.forbidRecipeKind(
        recipeKind, BookkeepingEntryKind.OPEN_ACCOUNTING_POSITION);
    if (fields.openingBalances() == null || fields.openingBalances().size() < 2) {
      throw new IllegalArgumentException(
          "openingBalances must contain at least two opening balances for openAccountingPosition.");
    }
    ContractPostingTemplateFieldRules.forbidText(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.equityAccountCode(), "equityAccountCode");
    ContractPostingTemplateFieldRules.forbidLines(fields.lines());
    ContractPostingTemplateFieldRules.forbidAmount(fields.amount(), "openAccountingPosition");
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static void validateReversalAdjustmentTemplate(
      @Nullable JournalRecipeKind recipeKind,
      PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    ContractPostingTemplateFieldRules.forbidRecipeKind(
        recipeKind, BookkeepingEntryKind.REVERSAL_ADJUSTMENT);
    ContractPostingTemplateFieldRules.requireLines(fields.lines(), "reversalAdjustment");
    ContractPostingTemplateFieldRules.forbidOpeningBalances(fields.openingBalances());
    ContractPostingTemplateFieldRules.forbidText(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.equityAccountCode(), "equityAccountCode");
    ContractPostingTemplateFieldRules.forbidAmount(fields.amount(), "reversalAdjustment");
    if (reversal == null) {
      throw new IllegalArgumentException("reversal must be present for reversalAdjustment.");
    }
  }

  record PostingTemplateFields(
      @Nullable String cashAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String equityAccountCode,
      @Nullable MonetaryAmount amount,
      @Nullable List<JournalLineTemplateDescriptor> lines,
      @Nullable List<OpeningBalanceTemplateDescriptor> openingBalances) {}
}
