package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Constructor normalization for public accrual cut-off entry variants. */
final class AccrualCutoffEntryConstructionSupport {
  record PrepaymentState(
      LocalDate effectiveDate,
      AccrualCutoffId accrualCutoffId,
      AccountCode prepaymentAssetAccountCode,
      AccountCode expenseAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      AccrualCutoffRecognitionInterval recognitionInterval) {}

  record DeferredRevenueState(
      LocalDate effectiveDate,
      AccrualCutoffId accrualCutoffId,
      AccountCode cashAccountCode,
      AccountCode deferredRevenueAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount amount,
      AccrualCutoffRecognitionInterval recognitionInterval) {}

  record AccruedExpenseState(
      LocalDate effectiveDate,
      AccrualCutoffId accrualCutoffId,
      AccountCode expenseAccountCode,
      AccountCode accruedExpenseLiabilityAccountCode,
      MonetaryAmount amount) {}

  record RecognitionState(
      LocalDate effectiveDate,
      AccrualCutoffId accrualCutoffId,
      MonetaryAmount amount,
      @Nullable ResolvedAccrualCutoffApplication resolvedApplication) {}

  record SettlementState(
      LocalDate effectiveDate,
      AccrualCutoffId accrualCutoffId,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      @Nullable ResolvedAccrualCutoffApplication resolvedApplication) {}

  private AccrualCutoffEntryConstructionSupport() {}

  static PrepaymentState prepayment(
      LocalDate effectiveDate,
      AccrualCutoffId accrualCutoffId,
      AccountCode prepaymentAssetAccountCode,
      AccountCode expenseAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      AccrualCutoffRecognitionInterval recognitionInterval) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
    AccrualCutoffRecognitionInterval requiredRecognitionInterval =
        Objects.requireNonNull(recognitionInterval, "recognitionInterval");
    requireRecognitionIntervalStartsOnOrAfter(
        requiredRecognitionInterval, requiredEffectiveDate, "prepayment");
    return new PrepaymentState(
        requiredEffectiveDate,
        Objects.requireNonNull(accrualCutoffId, "accrualCutoffId"),
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            prepaymentAssetAccountCode, "prepaymentAssetAccountCode"),
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            expenseAccountCode, "expenseAccountCode"),
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            cashAccountCode, "cashAccountCode"),
        BookkeepingEntryScalarValidationSupport.requirePositiveAmount(amount, "amount"),
        requiredRecognitionInterval);
  }

  static DeferredRevenueState deferredRevenue(
      LocalDate effectiveDate,
      AccrualCutoffId accrualCutoffId,
      AccountCode cashAccountCode,
      AccountCode deferredRevenueAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount amount,
      AccrualCutoffRecognitionInterval recognitionInterval) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
    AccrualCutoffRecognitionInterval requiredRecognitionInterval =
        Objects.requireNonNull(recognitionInterval, "recognitionInterval");
    requireRecognitionIntervalStartsOnOrAfter(
        requiredRecognitionInterval, requiredEffectiveDate, "deferredRevenue");
    return new DeferredRevenueState(
        requiredEffectiveDate,
        Objects.requireNonNull(accrualCutoffId, "accrualCutoffId"),
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            cashAccountCode, "cashAccountCode"),
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            deferredRevenueAccountCode, "deferredRevenueAccountCode"),
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            revenueAccountCode, "revenueAccountCode"),
        BookkeepingEntryScalarValidationSupport.requirePositiveAmount(amount, "amount"),
        requiredRecognitionInterval);
  }

  static AccruedExpenseState accruedExpense(
      LocalDate effectiveDate,
      AccrualCutoffId accrualCutoffId,
      AccountCode expenseAccountCode,
      AccountCode accruedExpenseLiabilityAccountCode,
      MonetaryAmount amount) {
    return new AccruedExpenseState(
        BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate),
        Objects.requireNonNull(accrualCutoffId, "accrualCutoffId"),
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            expenseAccountCode, "expenseAccountCode"),
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            accruedExpenseLiabilityAccountCode, "accruedExpenseLiabilityAccountCode"),
        BookkeepingEntryScalarValidationSupport.requirePositiveAmount(amount, "amount"));
  }

  static RecognitionState recognition(
      LocalDate effectiveDate,
      AccrualCutoffId accrualCutoffId,
      MonetaryAmount amount,
      @Nullable ResolvedAccrualCutoffApplication resolvedApplication) {
    requireApplicationKind(
        resolvedApplication, dev.erst.fingrind.core.AccrualCutoffApplicationKind.RECOGNITION);
    return new RecognitionState(
        BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate),
        Objects.requireNonNull(accrualCutoffId, "accrualCutoffId"),
        BookkeepingEntryScalarValidationSupport.requirePositiveAmount(amount, "amount"),
        resolvedApplication);
  }

  static SettlementState accruedExpenseSettlement(
      LocalDate effectiveDate,
      AccrualCutoffId accrualCutoffId,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      @Nullable ResolvedAccrualCutoffApplication resolvedApplication) {
    requireApplicationKind(
        resolvedApplication, dev.erst.fingrind.core.AccrualCutoffApplicationKind.SETTLEMENT);
    return new SettlementState(
        BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate),
        Objects.requireNonNull(accrualCutoffId, "accrualCutoffId"),
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            cashAccountCode, "cashAccountCode"),
        BookkeepingEntryScalarValidationSupport.requirePositiveAmount(amount, "amount"),
        resolvedApplication);
  }

  private static void requireRecognitionIntervalStartsOnOrAfter(
      AccrualCutoffRecognitionInterval recognitionInterval,
      LocalDate effectiveDate,
      String entryName) {
    if (recognitionInterval.startDate().isBefore(effectiveDate)) {
      throw new IllegalArgumentException(
          entryName + " recognitionInterval.startDate must not precede effectiveDate.");
    }
  }

  private static void requireApplicationKind(
      @Nullable ResolvedAccrualCutoffApplication resolvedApplication,
      dev.erst.fingrind.core.AccrualCutoffApplicationKind expectedKind) {
    if (resolvedApplication != null && resolvedApplication.applicationKind() != expectedKind) {
      throw new IllegalArgumentException(
          "Resolved accrual cut-off application must use applicationKind "
              + expectedKind.wireValue()
              + ".");
    }
  }
}
