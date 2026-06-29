package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractTemplates.JournalLineTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.OpeningBalanceTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.ReversalTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.TaxSelectionTemplateDescriptor;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Posting-template validator owner for the discovery contract namespace. */
final class ContractPostingRequestTemplateValidators {
  private ContractPostingRequestTemplateValidators() {}

  static void validate(
      BookkeepingEntryKind entryKind,
      PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    Objects.requireNonNull(entryKind, "entryKind");
    if (entryKind == BookkeepingEntryKind.DIRECT_JOURNAL) {
      validateDirectJournalTemplate(fields, reversal);
      return;
    }
    if (entryKind == BookkeepingEntryKind.SALE) {
      validateSaleTemplate(fields, reversal);
      return;
    }
    if (entryKind == BookkeepingEntryKind.EXPENSE) {
      validateExpenseTemplate(fields, reversal);
      return;
    }
    if (entryKind == BookkeepingEntryKind.OWNER_CONTRIBUTION) {
      validateOwnerContributionTemplate(fields, reversal);
      return;
    }
    if (entryKind == BookkeepingEntryKind.OWNER_WITHDRAWAL) {
      validateOwnerWithdrawalTemplate(fields, reversal);
      return;
    }
    if (entryKind == BookkeepingEntryKind.OPENING_POSITION) {
      validateOpeningPositionTemplate(fields, reversal);
      return;
    }
    validateReversalTemplate(fields, reversal);
  }

  private static void validateDirectJournalTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    ContractPostingTemplateFieldRules.requireLines(fields.lines(), "journal");
    ContractPostingTemplateFieldRules.forbidText(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.equityAccountCode(), "equityAccountCode");
    ContractPostingTemplateFieldRules.forbidAmount(fields.amount(), "journal");
    ContractPostingTemplateFieldRules.forbidTax(fields.tax(), "journal");
    ContractPostingTemplateFieldRules.forbidOpeningBalances(fields.openingBalances());
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static void validateSaleTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
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

  private static void validateExpenseTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
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

  private static void validateOwnerContributionTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    ContractPostingTemplateFieldRules.requireText(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateFieldRules.requireText(fields.equityAccountCode(), "equityAccountCode");
    ContractPostingTemplateFieldRules.requirePositiveAmount(fields.amount());
    ContractPostingTemplateFieldRules.forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    ContractPostingTemplateFieldRules.forbidLines(fields.lines());
    ContractPostingTemplateFieldRules.forbidOpeningBalances(fields.openingBalances());
    ContractPostingTemplateFieldRules.forbidTax(fields.tax(), "ownerContribution");
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static void validateOwnerWithdrawalTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    ContractPostingTemplateFieldRules.requireText(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateFieldRules.requireText(fields.equityAccountCode(), "equityAccountCode");
    ContractPostingTemplateFieldRules.requirePositiveAmount(fields.amount());
    ContractPostingTemplateFieldRules.forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    ContractPostingTemplateFieldRules.forbidLines(fields.lines());
    ContractPostingTemplateFieldRules.forbidOpeningBalances(fields.openingBalances());
    ContractPostingTemplateFieldRules.forbidTax(fields.tax(), "ownerWithdrawal");
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static void validateOpeningPositionTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    if (fields.openingBalances() == null || fields.openingBalances().size() < 2) {
      throw new IllegalArgumentException(
          "openingBalances must contain at least two opening balances for openingPosition.");
    }
    ContractPostingTemplateFieldRules.forbidText(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.equityAccountCode(), "equityAccountCode");
    ContractPostingTemplateFieldRules.forbidLines(fields.lines());
    ContractPostingTemplateFieldRules.forbidAmount(fields.amount(), "openingPosition");
    ContractPostingTemplateFieldRules.forbidTax(fields.tax(), "openingPosition");
    ContractPostingTemplateFieldRules.forbidForeignExchange(
        fields.foreignExchange(), "openingPosition");
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static void validateReversalTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    ContractPostingTemplateFieldRules.requireLines(fields.lines(), "reversal");
    ContractPostingTemplateFieldRules.forbidOpeningBalances(fields.openingBalances());
    ContractPostingTemplateFieldRules.forbidText(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    ContractPostingTemplateFieldRules.forbidText(fields.equityAccountCode(), "equityAccountCode");
    ContractPostingTemplateFieldRules.forbidAmount(fields.amount(), "reversal");
    ContractPostingTemplateFieldRules.forbidTax(fields.tax(), "reversal");
    if (reversal == null) {
      throw new IllegalArgumentException("reversal must be present for reversal.");
    }
  }

  record PostingTemplateFields(
      @Nullable String cashAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String equityAccountCode,
      @Nullable MonetaryAmount amount,
      @Nullable ForeignExchangeTemplateDescriptor foreignExchange,
      @Nullable TaxSelectionTemplateDescriptor tax,
      @Nullable List<JournalLineTemplateDescriptor> lines,
      @Nullable List<OpeningBalanceTemplateDescriptor> openingBalances) {}
}
