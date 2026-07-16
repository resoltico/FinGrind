package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.PostingAccrualCutoffRejectionSemantics;

/** Executor-local accrual cut-off violations derived from canonical contract rejections. */
public final class AccrualCutoffEntrySemanticsViolations {
  private AccrualCutoffEntrySemanticsViolations() {}

  /** Creates the rejection for an accrual cut-off event on a cash-basis book. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation requiresAccrualBasis(
      String selectorField, String selectorValue) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingAccrualCutoffRejectionSemantics.requiresAccrualBasis(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue)));
  }
}
