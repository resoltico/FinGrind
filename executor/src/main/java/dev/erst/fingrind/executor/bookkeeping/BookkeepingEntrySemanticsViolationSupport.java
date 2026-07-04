package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import java.util.Objects;

/** Package-private helpers that adapt canonical contract violations into executor-local ones. */
final class BookkeepingEntrySemanticsViolationSupport {
  static final String CANONICAL_SELECTOR_FIELD = "entryKind";

  private BookkeepingEntrySemanticsViolationSupport() {}

  static BookkeepingPostingRejection.EntrySemanticsViolation toLocal(
      PostingRejection.EntrySemanticsViolation violation) {
    PostingRejection.EntrySemanticsViolation requiredViolation =
        Objects.requireNonNull(violation, "violation");
    return new BookkeepingPostingRejection.EntrySemanticsViolation(
        requiredViolation.code(), requiredViolation.field(), requiredViolation.message());
  }

  static void requireCanonicalSelectorField(String selectorField) {
    if (!CANONICAL_SELECTOR_FIELD.equals(Objects.requireNonNull(selectorField, "selectorField"))) {
      throw new IllegalArgumentException(
          "selectorField must be '%s'.".formatted(CANONICAL_SELECTOR_FIELD));
    }
  }

  static String requireSelectorValue(String selectorValue) {
    return Objects.requireNonNull(selectorValue, "selectorValue");
  }
}
