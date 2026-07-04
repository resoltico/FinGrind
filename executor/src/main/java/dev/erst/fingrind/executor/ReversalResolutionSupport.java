package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolves reversal requests from referenced committed postings before journal-based handling. */
final class ReversalResolutionSupport {
  private ReversalResolutionSupport() {}

  static Optional<BookkeepingPostingRejection> rejectionFor(
      BookkeepingEntry.Reversal reversal, PostingValidationStore book) {
    Objects.requireNonNull(reversal, "reversal");
    Objects.requireNonNull(book, "book");
    PostingId priorPostingId = reversal.reversal().reference().priorPostingId();
    return book.findPosting(priorPostingId).isPresent()
        ? Optional.empty()
        : Optional.of(new BookkeepingPostingRejection.ReversalTargetNotFound(priorPostingId));
  }

  static BookkeepingEntry resolve(BookkeepingEntry entry, PostingValidationStore book) {
    Objects.requireNonNull(entry, "entry");
    Objects.requireNonNull(book, "book");
    if (entry instanceof BookkeepingEntry.Reversal reversal) {
      return resolve(reversal, book);
    }
    return entry;
  }

  static BookkeepingEntry.Reversal resolve(
      BookkeepingEntry.Reversal reversal, PostingValidationStore book) {
    Objects.requireNonNull(reversal, "reversal");
    Objects.requireNonNull(book, "book");
    if (reversal.resolvedJournalEntry() != null) {
      return reversal;
    }
    PostingId priorPostingId = reversal.reversal().reference().priorPostingId();
    CommittedPosting priorPosting =
        book.findPosting(priorPostingId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Reversal target "
                            + priorPostingId.value()
                            + " must exist before translation."));
    return new BookkeepingEntry.Reversal(
        reversal.effectiveDate(),
        reversal.reversal(),
        reversal.foreignExchangeDetails(),
        negatedJournalEntry(reversal.effectiveDate(), priorPosting.journalEntry()));
  }

  private static JournalEntry negatedJournalEntry(
      LocalDate effectiveDate, JournalEntry originalJournalEntry) {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(originalJournalEntry, "originalJournalEntry");
    List<JournalLine> negatedLines =
        originalJournalEntry.lines().stream().map(ReversalResolutionSupport::negatedLine).toList();
    return new JournalEntry(effectiveDate, negatedLines);
  }

  private static JournalLine negatedLine(JournalLine line) {
    Objects.requireNonNull(line, "line");
    return new JournalLine(line.accountCode(), opposite(line.side()), line.amount());
  }

  private static JournalLine.EntrySide opposite(JournalLine.EntrySide side) {
    return switch (side) {
      case DEBIT -> JournalLine.EntrySide.CREDIT;
      case CREDIT -> JournalLine.EntrySide.DEBIT;
    };
  }
}
