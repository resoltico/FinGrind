package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;

/** Canonical public rejection vocabulary for financing lifecycle admission. */
public final class PostingFinancingRejectionSemantics {
  private PostingFinancingRejectionSemantics() {}

  /** Returns one refusal for reversing a borrowing while a dependent lifecycle event remains. */
  public static PostingRejection.EntrySemanticsViolation
      borrowingReversalRequiresApplicationsReversed(
          dev.erst.fingrind.core.BookkeepingEntryKind entryKind,
          FinancingArrangementId financingArrangementId) {
    return violation(
            "financing-borrowing-reversal-requires-applications-reversed",
            "reversal.priorPostingId",
            "entryKind '%s' cannot reverse the borrowing for financingArrangementId '%s' while principal repayment, interest accrual, or interest payment applications remain active."
                .formatted(
                    Objects.requireNonNull(entryKind, "entryKind").wireValue(),
                    Objects.requireNonNull(financingArrangementId, "financingArrangementId")
                        .value()))
        .toPostingRejection();
  }

  private static Violation violation(String code, String field, String message) {
    return new Violation(code, field, message);
  }

  private record Violation(String code, String field, String message) {
    private PostingRejection.EntrySemanticsViolation toPostingRejection() {
      return new PostingRejection.EntrySemanticsViolation(code, field, message);
    }
  }
}
