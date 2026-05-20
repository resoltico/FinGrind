package dev.erst.fingrind.executor.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Validates that posting effective dates remain outside closed horizons. */
final class PostingClosedPeriodPolicy {
  Optional<BookkeepingPostingRejection> rejectionFor(
      PostingRequestModel postingRequest, PostingValidationStore book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(book, "book");
    LocalDate effectiveDate = postingRequest.journalEntry().effectiveDate();
    return book.closedThroughEffectiveDate()
        .filter(closedThrough -> !effectiveDate.isAfter(closedThrough))
        .<BookkeepingPostingRejection>map(
            closedThrough ->
                new BookkeepingPostingRejection.ClosedPeriodViolation(
                    closedThrough, effectiveDate));
  }
}
