package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccrualCutoffKind;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Exact durable lifecycle totals for one accrual cut-off aggregate. */
public record AccrualCutoffScheduleRow(
    AccrualCutoffId accrualCutoffId,
    AccrualCutoffKind kind,
    LocalDate originatedOn,
    AccountCode cutoffAccountCode,
    AccountCode recognitionAccountCode,
    MonetaryAmount originalAmount,
    MonetaryAmount appliedAmount,
    MonetaryAmount remainingAmount,
    Optional<LocalDate> recognitionStartDate,
    Optional<LocalDate> recognitionEndDate,
    Optional<LocalDate> latestApplicationEffectiveDate) {
  /** Validates exact amounts, account facts, and optional schedule boundaries. */
  public AccrualCutoffScheduleRow {
    Objects.requireNonNull(accrualCutoffId, "accrualCutoffId");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(originatedOn, "originatedOn");
    Objects.requireNonNull(cutoffAccountCode, "cutoffAccountCode");
    Objects.requireNonNull(recognitionAccountCode, "recognitionAccountCode");
    Objects.requireNonNull(originalAmount, "originalAmount");
    Objects.requireNonNull(appliedAmount, "appliedAmount");
    Objects.requireNonNull(remainingAmount, "remainingAmount");
    Objects.requireNonNull(recognitionStartDate, "recognitionStartDate");
    Objects.requireNonNull(recognitionEndDate, "recognitionEndDate");
    Objects.requireNonNull(latestApplicationEffectiveDate, "latestApplicationEffectiveDate");
    if (!originalAmount.currencyCode().equals(appliedAmount.currencyCode())
        || !originalAmount.currencyCode().equals(remainingAmount.currencyCode())) {
      throw new IllegalArgumentException(
          "Accrual cut-off schedule amounts must share one currency.");
    }
    if (!originalAmount.toMoney().equals(appliedAmount.toMoney().plus(remainingAmount.toMoney()))) {
      throw new IllegalArgumentException(
          "Accrual cut-off schedule originalAmount must equal appliedAmount plus remainingAmount.");
    }
    if (recognitionStartDate.isPresent() != recognitionEndDate.isPresent()) {
      throw new IllegalArgumentException(
          "Accrual cut-off schedule recognition boundaries must be present together.");
    }
    if (kind == AccrualCutoffKind.ACCRUED_EXPENSE && recognitionStartDate.isPresent()) {
      throw new IllegalArgumentException(
          "Accrued expense must not publish recognition boundaries.");
    }
    if (kind != AccrualCutoffKind.ACCRUED_EXPENSE && recognitionStartDate.isEmpty()) {
      throw new IllegalArgumentException(
          "Recognizable accrual cut-offs require recognition boundaries.");
    }
  }
}
