package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import java.util.List;
import java.util.Optional;

/** Post-tax entry resolution owner for typed-entry and reversal request expansion. */
final class PostEntryResolutionSupport {
  private PostEntryResolutionSupport() {}

  static ResolutionOutcome resolveAfterTaxValidation(
      BookkeepingEntry entry,
      PostingValidationStore book,
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      int violationCountBeforeTaxValidation) {
    if (violations.size() != violationCountBeforeTaxValidation) {
      return new ResolutionOutcome(entry, Optional.empty());
    }
    BookkeepingEntry resolvedEntry = TaxPostingResolution.resolve(entry, book);
    if (!violations.isEmpty()) {
      return new ResolutionOutcome(resolvedEntry, Optional.empty());
    }
    Optional<BookkeepingPostingRejection> reversalRejection =
        reversalResolutionRejection(resolvedEntry, book);
    if (reversalRejection.isPresent()) {
      return new ResolutionOutcome(resolvedEntry, reversalRejection);
    }
    return new ResolutionOutcome(resolvedReversalEntry(resolvedEntry, book), Optional.empty());
  }

  private static Optional<BookkeepingPostingRejection> reversalResolutionRejection(
      BookkeepingEntry entry, PostingValidationStore book) {
    return entry instanceof BookkeepingEntry.Reversal reversal
        ? ReversalResolutionSupport.rejectionFor(reversal, book)
        : Optional.empty();
  }

  private static BookkeepingEntry resolvedReversalEntry(
      BookkeepingEntry entry, PostingValidationStore book) {
    return entry instanceof BookkeepingEntry.Reversal reversal
        ? ReversalResolutionSupport.resolve(reversal, book)
        : entry;
  }

  record ResolutionOutcome(
      BookkeepingEntry entry, Optional<BookkeepingPostingRejection> rejection) {}
}
