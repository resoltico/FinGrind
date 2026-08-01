package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;

/** Canonical public rejection vocabulary for fixed-asset lifecycle admission. */
public final class PostingFixedAssetRejectionSemantics {
  private PostingFixedAssetRejectionSemantics() {}

  /**
   * Returns one refusal for reversing a capitalization while a dependent lifecycle event remains.
   */
  public static PostingRejection.EntrySemanticsViolation
      capitalizationReversalRequiresApplicationsReversed(
          dev.erst.fingrind.core.BookkeepingEntryKind entryKind, FixedAssetId fixedAssetId) {
    return violation(
            "fixed-asset-capitalization-reversal-requires-applications-reversed",
            "reversal.priorPostingId",
            "entryKind '%s' cannot reverse the capitalization for fixedAssetId '%s' while depreciation or disposal applications remain active."
                .formatted(
                    Objects.requireNonNull(entryKind, "entryKind").wireValue(),
                    Objects.requireNonNull(fixedAssetId, "fixedAssetId").value()))
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
