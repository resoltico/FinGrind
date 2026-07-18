package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import java.util.List;

/** Adapts one published entry-semantics violation into the executor-local posting rejection. */
final class ReversalEntrySemanticsRejectionMapper {
  private ReversalEntrySemanticsRejectionMapper() {}

  static BookkeepingPostingRejection.EntrySemanticsViolations toLocal(
      PostingRejection.EntrySemanticsViolation violation) {
    return new BookkeepingPostingRejection.EntrySemanticsViolations(
        List.of(BookkeepingEntrySemanticsViolationSupport.toLocal(violation)));
  }
}
