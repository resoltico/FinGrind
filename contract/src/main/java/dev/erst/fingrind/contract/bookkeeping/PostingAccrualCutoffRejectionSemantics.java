package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccrualCutoffKind;
import java.time.LocalDate;
import java.util.Objects;

/** Canonical public rejection vocabulary for accrual cut-off lifecycle admission. */
public final class PostingAccrualCutoffRejectionSemantics {
  private PostingAccrualCutoffRejectionSemantics() {}

  /** Returns one refusal for a cut-off command on a cash-basis book. */
  public static PostingRejection.EntrySemanticsViolation requiresAccrualBasis(
      String selectorValue) {
    return violation(
            "accrual-cutoff-requires-accrual-basis",
            "entryKind",
            "%s '%s' requires an ACCRUAL accountingBasis."
                .formatted("entryKind", requireSelectorValue(selectorValue)))
        .toPostingRejection();
  }

  /** Returns one refusal for a duplicate cut-off identifier. */
  public static PostingRejection.EntrySemanticsViolation idAlreadyExists(
      String selectorValue, AccrualCutoffId accrualCutoffId) {
    return violation(
            "accrual-cutoff-id-already-exists",
            "accrualCutoffId",
            "entryKind '%s' cannot create accrualCutoffId '%s' because that identifier already exists."
                .formatted(requireSelectorValue(selectorValue), requireId(accrualCutoffId).value()))
        .toPostingRejection();
  }

  /** Returns one refusal for an unknown cut-off identifier. */
  public static PostingRejection.EntrySemanticsViolation notFound(
      String selectorValue, AccrualCutoffId accrualCutoffId) {
    return violation(
            "accrual-cutoff-not-found",
            "accrualCutoffId",
            "entryKind '%s' cannot find accrualCutoffId '%s' in this book."
                .formatted(requireSelectorValue(selectorValue), requireId(accrualCutoffId).value()))
        .toPostingRejection();
  }

  /** Returns one refusal for an application that contradicts the cut-off kind. */
  public static PostingRejection.EntrySemanticsViolation applicationKindNotAdmitted(
      String selectorValue, AccrualCutoffId accrualCutoffId, AccrualCutoffKind accrualCutoffKind) {
    return violation(
            "accrual-cutoff-application-kind-not-admitted",
            "accrualCutoffId",
            "entryKind '%s' does not admit this application for accrualCutoffId '%s' with kind '%s'."
                .formatted(
                    requireSelectorValue(selectorValue),
                    requireId(accrualCutoffId).value(),
                    Objects.requireNonNull(accrualCutoffKind, "accrualCutoffKind").wireValue()))
        .toPostingRejection();
  }

  /** Returns one refusal for recognition outside its declared inclusive interval. */
  public static PostingRejection.EntrySemanticsViolation recognitionOutsideInterval(
      String selectorValue,
      AccrualCutoffId accrualCutoffId,
      LocalDate effectiveDate,
      LocalDate intervalStartDate,
      LocalDate intervalEndDate) {
    return violation(
            "accrual-cutoff-application-outside-recognition-interval",
            "effectiveDate",
            "entryKind '%s' uses effectiveDate '%s' outside the inclusive recognition interval %s through %s for accrualCutoffId '%s'."
                .formatted(
                    requireSelectorValue(selectorValue),
                    Objects.requireNonNull(effectiveDate, "effectiveDate"),
                    Objects.requireNonNull(intervalStartDate, "intervalStartDate"),
                    Objects.requireNonNull(intervalEndDate, "intervalEndDate"),
                    requireId(accrualCutoffId).value()))
        .toPostingRejection();
  }

  /** Returns one refusal for an application that backdates a cut-off lifecycle. */
  public static PostingRejection.EntrySemanticsViolation applicationPrecedesHorizon(
      String selectorValue,
      AccrualCutoffId accrualCutoffId,
      LocalDate effectiveDate,
      LocalDate horizonEffectiveDate) {
    return violation(
            "accrual-cutoff-application-precedes-horizon",
            "effectiveDate",
            "entryKind '%s' uses effectiveDate '%s' before the lifecycle horizon '%s' for accrualCutoffId '%s'."
                .formatted(
                    requireSelectorValue(selectorValue),
                    Objects.requireNonNull(effectiveDate, "effectiveDate"),
                    Objects.requireNonNull(horizonEffectiveDate, "horizonEffectiveDate"),
                    requireId(accrualCutoffId).value()))
        .toPostingRejection();
  }

  /** Returns one refusal for an application larger than the remaining cut-off amount. */
  public static PostingRejection.EntrySemanticsViolation applicationExceedsRemainingAmount(
      String selectorValue,
      AccrualCutoffId accrualCutoffId,
      MonetaryAmount requestedAmount,
      MonetaryAmount remainingAmount) {
    return violation(
            "accrual-cutoff-application-exceeds-remaining-amount",
            "amount",
            "entryKind '%s' requests amount '%s %s' but accrualCutoffId '%s' has only '%s %s' remaining."
                .formatted(
                    requireSelectorValue(selectorValue),
                    Objects.requireNonNull(requestedAmount, "requestedAmount").currencyCode(),
                    requestedAmount.minorUnits(),
                    requireId(accrualCutoffId).value(),
                    Objects.requireNonNull(remainingAmount, "remainingAmount").currencyCode(),
                    remainingAmount.minorUnits()))
        .toPostingRejection();
  }

  /** Returns one refusal for a reversal that would break the aggregate's durable date horizon. */
  public static PostingRejection.EntrySemanticsViolation reversalPrecedesHorizon(
      dev.erst.fingrind.core.BookkeepingEntryKind entryKind,
      AccrualCutoffId accrualCutoffId,
      LocalDate effectiveDate,
      LocalDate horizonEffectiveDate) {
    return violation(
            "accrual-cutoff-reversal-precedes-horizon",
            "effectiveDate",
            "entryKind '%s' uses effectiveDate '%s' before the lifecycle horizon '%s' for accrualCutoffId '%s'."
                .formatted(
                    Objects.requireNonNull(entryKind, "entryKind").wireValue(),
                    Objects.requireNonNull(effectiveDate, "effectiveDate"),
                    Objects.requireNonNull(horizonEffectiveDate, "horizonEffectiveDate"),
                    requireId(accrualCutoffId).value()))
        .toPostingRejection();
  }

  /** Returns one refusal for an origin reversal while lifecycle applications remain active. */
  public static PostingRejection.EntrySemanticsViolation originReversalRequiresZeroApplications(
      dev.erst.fingrind.core.BookkeepingEntryKind entryKind, AccrualCutoffId accrualCutoffId) {
    return violation(
            "accrual-cutoff-origin-reversal-requires-zero-applications",
            "reversal.priorPostingId",
            "entryKind '%s' cannot reverse the origin for accrualCutoffId '%s' while recognition or settlement applications remain active."
                .formatted(
                    Objects.requireNonNull(entryKind, "entryKind").wireValue(),
                    requireId(accrualCutoffId).value()))
        .toPostingRejection();
  }

  private static Violation violation(String code, String field, String message) {
    return new Violation(code, field, message);
  }

  private static String requireSelectorValue(String selectorValue) {
    return PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
  }

  private static AccrualCutoffId requireId(AccrualCutoffId accrualCutoffId) {
    return Objects.requireNonNull(accrualCutoffId, "accrualCutoffId");
  }

  private record Violation(String code, String field, String message) {
    private PostingRejection.EntrySemanticsViolation toPostingRejection() {
      return new PostingRejection.EntrySemanticsViolation(code, field, message);
    }
  }
}
