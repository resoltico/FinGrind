package dev.erst.fingrind.executor.bookkeeping;

import java.util.Objects;

/** Signals one inventory admission boundary that maps to one published entry-semantics issue. */
final class InventoryEntrySemanticsFailure extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final transient BookkeepingPostingRejection.EntrySemanticsViolation violation;

  InventoryEntrySemanticsFailure(
      BookkeepingPostingRejection.EntrySemanticsViolation violation, Throwable cause) {
    super("Inventory admission reached one entry-semantics rejection.", cause);
    this.violation = Objects.requireNonNull(violation, "violation");
  }

  BookkeepingPostingRejection.EntrySemanticsViolation violation() {
    return violation;
  }
}
