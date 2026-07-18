package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;

/** Canonical public rejection vocabulary for realized foreign-exchange lifecycle admission. */
public final class PostingRealizedForeignExchangeRejectionSemantics {
  private PostingRealizedForeignExchangeRejectionSemantics() {}

  /** Returns one refusal for reversing an obligation while its settlement remains active. */
  public static PostingRejection.EntrySemanticsViolation
      obligationReversalRequiresSettlementReversed(
          dev.erst.fingrind.core.BookkeepingEntryKind entryKind,
          ForeignCurrencyObligationId foreignCurrencyObligationId) {
    return violation(
            "foreign-currency-obligation-reversal-requires-settlement-reversed",
            "reversal.priorPostingId",
            "entryKind '%s' cannot reverse foreignCurrencyObligationId '%s' while its settlement remains active."
                .formatted(
                    Objects.requireNonNull(entryKind, "entryKind").wireValue(),
                    Objects.requireNonNull(
                            foreignCurrencyObligationId, "foreignCurrencyObligationId")
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
