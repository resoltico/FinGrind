package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractTemplates.JournalLineTemplateDescriptor;
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
        BookkeepingEntryKind.OPENING_BALANCE_ADJUSTMENT,
        (fields, reversal) -> validateOpeningBalanceAdjustmentTemplate(fields),
        BookkeepingEntryKind.CORRECTION_ADJUSTMENT,
        ContractPostingRequestTemplateValidators::validateCorrectionAdjustmentTemplate,
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
    forbidReversal(reversal);
  }

  private static void validateOpeningBalanceAdjustmentTemplate(PostingTemplateFields fields) {
    if (fields.lines() == null || fields.lines().size() < 2) {
      throw new IllegalArgumentException(
          "lines must contain at least two journal lines for openingBalanceAdjustment.");
    }
    forbidText(fields.cashAccountCode(), "cashAccountCode");
    forbidText(fields.revenueAccountCode(), "revenueAccountCode");
    forbidText(fields.expenseAccountCode(), "expenseAccountCode");
    forbidText(fields.equityAccountCode(), "equityAccountCode");
    if (fields.amount() != null) {
      throw new IllegalArgumentException("amount must be absent for openingBalanceAdjustment.");
    }
  }

  private static void validateCorrectionAdjustmentTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    validateOpeningBalanceAdjustmentTemplate(fields);
    forbidReversal(reversal);
  }

  private static void validateReversalAdjustmentTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    validateOpeningBalanceAdjustmentTemplate(fields);
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
      throw new IllegalArgumentException("lines must be absent for typed business events.");
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
      @Nullable List<JournalLineTemplateDescriptor> lines) {}
}
