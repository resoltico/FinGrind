package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractTemplates.JournalLineTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.OpeningBalanceTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.ReversalTemplateDescriptor;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Posting-template validator owner for the discovery contract namespace. */
final class ContractPostingRequestTemplateValidators {
  private static final Map<BookkeepingEntryKind, PostingRequestTemplateValidator> VALIDATORS =
      validators();

  private ContractPostingRequestTemplateValidators() {}

  static void validate(
      BookkeepingEntryKind entryKind,
      PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    validatorFor(entryKind).validate(fields, reversal);
  }

  private static PostingRequestTemplateValidator validatorFor(BookkeepingEntryKind entryKind) {
    return java.util.Objects.requireNonNull(VALIDATORS.get(entryKind), "entryKind validator");
  }

  private static Map<BookkeepingEntryKind, PostingRequestTemplateValidator> validators() {
    return Map.of(
        BookkeepingEntryKind.CASH_REVENUE,
        ContractPostingRequestTemplateValidators::validateCashRevenueTemplate,
        BookkeepingEntryKind.CASH_EXPENSE,
        ContractPostingRequestTemplateValidators::validateCashExpenseTemplate,
        BookkeepingEntryKind.EQUITY_CONTRIBUTION,
        ContractPostingRequestTemplateValidators::validateEquityContributionTemplate,
        BookkeepingEntryKind.EQUITY_WITHDRAWAL,
        ContractPostingRequestTemplateValidators::validateEquityWithdrawalTemplate,
        BookkeepingEntryKind.OPEN_ACCOUNTING_POSITION,
        (fields, reversal) -> validateOpenAccountingPositionTemplate(fields),
        BookkeepingEntryKind.REVERSAL_ADJUSTMENT,
        ContractPostingRequestTemplateValidators::validateReversalAdjustmentTemplate);
  }

  private static void validateCashRevenueTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    requireText(fields.cashAccountCode(), "cashAccountCode");
    requireText(fields.revenueAccountCode(), "revenueAccountCode");
    requirePositiveAmount(fields.amount());
    forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    forbidText(fields.equityAccountCode(), "equityAccountCode");
    forbidLines(fields.lines());
    forbidOpeningBalances(fields.openingBalances());
    forbidReversal(reversal);
  }

  private static void validateCashExpenseTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    requireText(fields.cashAccountCode(), "cashAccountCode");
    requireText(fields.expenseAccountCode(), "expenseAccountCode");
    requirePositiveAmount(fields.amount());
    forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    forbidText(fields.equityAccountCode(), "equityAccountCode");
    forbidLines(fields.lines());
    forbidOpeningBalances(fields.openingBalances());
    forbidReversal(reversal);
  }

  private static void validateEquityContributionTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    requireText(fields.cashAccountCode(), "cashAccountCode");
    requireText(fields.equityAccountCode(), "equityAccountCode");
    requirePositiveAmount(fields.amount());
    forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    forbidLines(fields.lines());
    forbidOpeningBalances(fields.openingBalances());
    forbidReversal(reversal);
  }

  private static void validateEquityWithdrawalTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    requireText(fields.cashAccountCode(), "cashAccountCode");
    requireText(fields.equityAccountCode(), "equityAccountCode");
    requirePositiveAmount(fields.amount());
    forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    forbidLines(fields.lines());
    forbidOpeningBalances(fields.openingBalances());
    forbidReversal(reversal);
  }

  private static void validateOpenAccountingPositionTemplate(PostingTemplateFields fields) {
    if (fields.openingBalances() == null || fields.openingBalances().size() < 2) {
      throw new IllegalArgumentException(
          "openingBalances must contain at least two opening balances for openAccountingPosition.");
    }
    forbidText(fields.cashAccountCode(), "cashAccountCode");
    forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    forbidText(fields.equityAccountCode(), "equityAccountCode");
    if (fields.lines() != null) {
      throw new IllegalArgumentException("lines must be absent for openAccountingPosition.");
    }
    if (fields.amount() != null) {
      throw new IllegalArgumentException("amount must be absent for openAccountingPosition.");
    }
  }

  private static void validateReversalAdjustmentTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    requireLines(fields.lines(), "reversalAdjustment");
    forbidOpeningBalances(fields.openingBalances());
    forbidText(fields.cashAccountCode(), "cashAccountCode");
    forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    forbidText(fields.equityAccountCode(), "equityAccountCode");
    if (fields.amount() != null) {
      throw new IllegalArgumentException("amount must be absent for reversalAdjustment.");
    }
    if (reversal == null) {
      throw new IllegalArgumentException("reversal must be present for reversalAdjustment.");
    }
  }

  private static String requireText(@Nullable String value, String fieldName) {
    if (value == null) {
      throw new IllegalArgumentException(fieldName + " must not be null.");
    }
    return ContractDescriptorValidation.requireText(value, fieldName);
  }

  private static void forbidText(@Nullable String value, String fieldName) {
    if (value != null) {
      throw new IllegalArgumentException(fieldName + " must be absent for this entryKind.");
    }
  }

  private static MonetaryAmount requirePositiveAmount(@Nullable MonetaryAmount amount) {
    if (amount == null) {
      throw new IllegalArgumentException("amount must not be null.");
    }
    MonetaryAmount requiredAmount = ContractDescriptorValidation.requireValue(amount, "amount");
    if (!requiredAmount.toMoney().isPositive()) {
      throw new IllegalArgumentException("amount must carry one positive minor-unit value.");
    }
    return requiredAmount;
  }

  private static void forbidLines(@Nullable List<JournalLineTemplateDescriptor> lines) {
    if (lines != null) {
      throw new IllegalArgumentException("lines must be absent for this entryKind.");
    }
  }

  private static void requireLines(
      @Nullable List<JournalLineTemplateDescriptor> lines, String entryKindName) {
    if (lines == null || lines.size() < 2) {
      throw new IllegalArgumentException(
          "lines must contain at least two journal lines for " + entryKindName + ".");
    }
  }

  private static void forbidOpeningBalances(
      @Nullable List<OpeningBalanceTemplateDescriptor> openingBalances) {
    if (openingBalances != null) {
      throw new IllegalArgumentException("openingBalances must be absent for this entryKind.");
    }
  }

  private static void forbidReversal(@Nullable ReversalTemplateDescriptor reversal) {
    if (reversal != null) {
      throw new IllegalArgumentException("reversal must be absent for this entryKind.");
    }
  }

  /** Validates the field combination for one posting template entry kind. */
  @FunctionalInterface
  private interface PostingRequestTemplateValidator {
    /** Validates the extracted posting fields and optional reversal details. */
    void validate(PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal);
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
