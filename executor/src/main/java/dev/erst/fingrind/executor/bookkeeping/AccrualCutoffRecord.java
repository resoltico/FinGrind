package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccrualCutoffKind;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Executor read model for one durable accrual cut-off aggregate and its consumed amount. */
public sealed interface AccrualCutoffRecord
    permits AccrualCutoffRecord.Prepayment,
        AccrualCutoffRecord.DeferredRevenue,
        AccrualCutoffRecord.AccruedExpense {
  /** Returns the durable identity of this cut-off aggregate. */
  AccrualCutoffId accrualCutoffId();

  /** Returns the business kind that owns the aggregate lifecycle. */
  AccrualCutoffKind kind();

  /** Returns the effective date on which the originating event was posted. */
  LocalDate originatedOn();

  /** Returns the total originating amount before lifecycle applications. */
  Money originalAmount();

  /** Returns the amount consumed by durable recognition or settlement applications. */
  Money appliedAmount();

  /** Returns the latest effective date consumed by this aggregate's lifecycle. */
  Optional<LocalDate> latestApplicationEffectiveDate();

  /** Returns the original amount less all durable recognition or settlement applications. */
  default Money remainingAmount() {
    return originalAmount().minus(appliedAmount());
  }

  /** Returns the earliest effective date allowed for the next lifecycle application. */
  default LocalDate lifecycleHorizonEffectiveDate() {
    return latestApplicationEffectiveDate().orElse(originatedOn());
  }

  /** One prepayment cut-off with an asset-to-expense recognition path. */
  record Prepayment(
      AccrualCutoffId accrualCutoffId,
      LocalDate originatedOn,
      AccountCode prepaymentAssetAccountCode,
      AccountCode expenseAccountCode,
      Money originalAmount,
      AccrualCutoffRecognitionInterval recognitionInterval,
      Money appliedAmount,
      Optional<LocalDate> latestApplicationEffectiveDate)
      implements AccrualCutoffRecord {
    public Prepayment {
      validateCommon(
          accrualCutoffId,
          originatedOn,
          originalAmount,
          appliedAmount,
          latestApplicationEffectiveDate);
      Objects.requireNonNull(prepaymentAssetAccountCode, "prepaymentAssetAccountCode");
      Objects.requireNonNull(expenseAccountCode, "expenseAccountCode");
      Objects.requireNonNull(recognitionInterval, "recognitionInterval");
      if (recognitionInterval.startDate().isBefore(originatedOn)) {
        throw new IllegalArgumentException(
            "Prepayment recognition interval must not precede originatedOn.");
      }
    }

    @Override
    public AccrualCutoffKind kind() {
      return AccrualCutoffKind.PREPAYMENT;
    }
  }

  /** One deferred-revenue cut-off with a liability-to-revenue recognition path. */
  record DeferredRevenue(
      AccrualCutoffId accrualCutoffId,
      LocalDate originatedOn,
      AccountCode deferredRevenueAccountCode,
      AccountCode revenueAccountCode,
      Money originalAmount,
      AccrualCutoffRecognitionInterval recognitionInterval,
      Money appliedAmount,
      Optional<LocalDate> latestApplicationEffectiveDate)
      implements AccrualCutoffRecord {
    public DeferredRevenue {
      validateCommon(
          accrualCutoffId,
          originatedOn,
          originalAmount,
          appliedAmount,
          latestApplicationEffectiveDate);
      Objects.requireNonNull(deferredRevenueAccountCode, "deferredRevenueAccountCode");
      Objects.requireNonNull(revenueAccountCode, "revenueAccountCode");
      Objects.requireNonNull(recognitionInterval, "recognitionInterval");
      if (recognitionInterval.startDate().isBefore(originatedOn)) {
        throw new IllegalArgumentException(
            "Deferred-revenue recognition interval must not precede originatedOn.");
      }
    }

    @Override
    public AccrualCutoffKind kind() {
      return AccrualCutoffKind.DEFERRED_REVENUE;
    }
  }

  /** One accrued-expense cut-off with a liability-to-cash settlement path. */
  record AccruedExpense(
      AccrualCutoffId accrualCutoffId,
      LocalDate originatedOn,
      AccountCode accruedExpenseLiabilityAccountCode,
      AccountCode expenseAccountCode,
      Money originalAmount,
      Money appliedAmount,
      Optional<LocalDate> latestApplicationEffectiveDate)
      implements AccrualCutoffRecord {
    public AccruedExpense {
      validateCommon(
          accrualCutoffId,
          originatedOn,
          originalAmount,
          appliedAmount,
          latestApplicationEffectiveDate);
      Objects.requireNonNull(
          accruedExpenseLiabilityAccountCode, "accruedExpenseLiabilityAccountCode");
      Objects.requireNonNull(expenseAccountCode, "expenseAccountCode");
    }

    @Override
    public AccrualCutoffKind kind() {
      return AccrualCutoffKind.ACCRUED_EXPENSE;
    }
  }

  private static void validateCommon(
      AccrualCutoffId accrualCutoffId,
      LocalDate originatedOn,
      Money originalAmount,
      Money appliedAmount,
      Optional<LocalDate> latestApplicationEffectiveDate) {
    Objects.requireNonNull(accrualCutoffId, "accrualCutoffId");
    Objects.requireNonNull(originatedOn, "originatedOn");
    Objects.requireNonNull(originalAmount, "originalAmount");
    Objects.requireNonNull(appliedAmount, "appliedAmount");
    Objects.requireNonNull(latestApplicationEffectiveDate, "latestApplicationEffectiveDate");
    if (!originalAmount.isPositive()) {
      throw new IllegalArgumentException("Accrual cut-off originalAmount must be positive.");
    }
    if (appliedAmount.compareTo(originalAmount) > 0) {
      throw new IllegalArgumentException(
          "Accrual cut-off appliedAmount must not exceed originalAmount.");
    }
    latestApplicationEffectiveDate.ifPresent(
        applicationDate -> {
          if (applicationDate.isBefore(originatedOn)) {
            throw new IllegalArgumentException(
                "Accrual cut-off application horizon must not precede originatedOn.");
          }
        });
  }
}
